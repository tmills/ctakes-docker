#!/usr/bin/env python3
import os
import sys
import tempfile
import time
from subprocess import call
import configparser
import datetime

import boto3
import paramiko
from paramiko.client import SSHClient

USER_KEY='ctakes_umlsuser'
PW_KEY='ctakes_umlspw'
broker_port=61616

ec2 = None
script_time = datetime.datetime.now().isoformat()

def main(args):
    if len(args) < 1:
        sys.stderr.write("Arguments: <config file>\n")
        sys.exit(-1)

    config = configparser.ConfigParser()
    config.read(args[0])
    config_map = config['default']

    ## Read in all the configuration parameters
    BROKER_AMI = config_map['BROKER_AMI']
    BROKER_MACHINE = config_map['BROKER_MACHINE']
    MIST_AMI = config_map['MIST_AMI']
    MIST_MACHINE = config_map['MIST_MACHINE']
    MIST_REPLICATION = int(config_map['MIST_REPLICATION'])
    PIPELINE_AMI = config_map['PIPELINE_AMI']
    PIPELINE_MACHINE = config_map['PIPELINE_MACHINE']
    PIPELINE_REPLICATION = int(config_map['PIPELINE_REPLICATION'])
    WRITER_AMI = config_map['WRITER_AMI']
    WRITER_MACHINE = config_map['WRITER_MACHINE']
    WRITER_REPLICATION = int(config_map['WRITER_REPLICATION'])
    key_pair = config_map['key_pair']
    key_file = config_map['key_file']
    security_groups = config_map['security_groups'].split(',')
    subnet_id = config_map['subnet_id']
    out_fn = config_map['id_file']

    ## TODO - Allow this to be defined in the properties file
    if not USER_KEY in os.environ.keys():
        sys.stderr.write("The environment variable %s must be defined for this script\n" % (USER_KEY))
        sys.exit(-1)

    if not PW_KEY in os.environ.keys():
        sys.stderr.write("The environment variable %s must be defined for this script\n" % (PW_KEY))
        sys.exit(-1)

    #collection_reader, pipeline, out_fn = args

    global ec2
    ec2 = boto3.client('ec2')
    ec2r = boto3.resource('ec2')

    fout = open(out_fn, 'w')

    ## Start the broker instance:
    broker_instances = start_instances(BROKER_AMI, 1, BROKER_MACHINE, "NLP-Broker-Autostart")
    log_instances(broker_instances, fout)
    broker_id = broker_instances['Instances'][0]['InstanceId']
    broker_instance = ec2r.Instance(broker_id)
    broker_instance.reload()
    broker_private_ip = broker_instance.private_ip_address
    broker_public_ip = broker_instance.public_ip_address
    broker_hostname = broker_instance.public_dns_name

    if broker_private_ip is None or broker_public_ip is None:
        raise Exception('Could not get IP address for broker')

    #fout.write('broker_id=%s\n' % (broker_id)); fout.flush()

    ## Write the env_file with information about the broker that the other
    ## containers will need.
    env_file = write_env_file(broker_private_ip, broker_port)
    #call(['scp', '-i', key_file, env_file.name, '%s:/home/ubuntu/ctakes-docker/env_file.txt' % broker_public_ip])

    ## Start the broker:
    run_command_on_instances(broker_instances, 'ctakes-docker/bin/runBrokerContainer.sh', env_file)

    ## start the mist instance(s):
    mist_instances = start_instances(MIST_AMI, MIST_REPLICATION, MIST_MACHINE, "NLP-Mist-Autostart")
    log_instances(mist_instances, fout)
    run_command_on_instances(mist_instances, "ctakes-docker/bin/runMistContainer.sh", env_file)

    ## start the ctakes instance:
    pipeline_instances = start_instances(PIPELINE_AMI, PIPELINE_REPLICATION, PIPELINE_MACHINE, "NLP-Pipeline-Autostart")
    log_instances(pipeline_instances, fout)
    run_command_on_instances(pipeline_instances, "ctakes-docker/bin/runPipelineContainer.sh", env_file)

    ## start the i2b2 writer instance:
    writer_instances = start_instances(WRITER_AMI, WRITER_REPLICATION, WRITER_MACHINE, "NLP-Writer-Autostart")
    log_instances(writer_instances, fout)
    run_command_on_instances(writer_instances, "ctakes-docker/bin/runWriterContainer.sh", env_file)

    env_file.close()
    fout.close()

    ## Start collection reader with pipeline

def log_instances(instances, fout):
    for instance in instances['Instances']:
        inst_id = instance['InstanceId']
        fout.write('instance_id=%s\n' % (inst_id)); fout.flush()

def run_command_on_instances(instances, command, env_file):
    ec2r = boto3.resource('ec2')

    ssh = SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    for instance in instances['Instances']:
        inst_id = instance['InstanceId']
        instance_obj = ec2r.Instance(inst_id)
        instance_obj.reload()
        inst_ip = instance_obj.public_dns_name
        sys.stderr.write("Copying env_file.txt to host...\n")
        call(['scp', '-i', key_file, env_file.name, 'ubuntu@%s:/home/ubuntu/ctakes-docker/env_file.txt' % inst_ip])
        time.sleep(2)
        sys.stderr.write("Running command %s on remote host...\n" % (command))
        ssh.connect(inst_ip, username='ubuntu', key_filename=key_file)
        stdin, stdout, stderr = ssh.exec_command(command)
        stdin.flush()
        output = stdout.read().splitlines()
        error = stderr.read().splitlines()
        sys.stderr.write('Startup standard output\n%s:Startup standard error:\n%s\n' % (output, error))
        ssh.close()

def start_instances(ami, count, inst_type, tag_name="NLP_Auto_Start"):
    global ec2
    instances = ec2.run_instances(ImageId=ami,
                                MinCount=1,
                                MaxCount=count,
                                InstanceType=inst_type,
                                KeyName=key_pair,
                                TagSpecifications=[
                                    {'ResourceType':'instance',
                                     'Tags':[
                                            {'Key':'Name', 'Value':tag_name},
                                            {'Key':'ScriptStart', 'Value':script_time}
                                        ]
                                    }],
                                SubnetId=subnet_id,
                                SecurityGroupIds=security_groups
                                )
    wait_for_all_to_run(instances)
    return instances

# Boto 3
# Use the filter() method of the instances collection to retrieve
# all running EC2 instances.
def instance_running(inst):
    global ec2

    inst_id = inst['InstanceId']
    statuses = ec2.describe_instance_status(InstanceIds=[inst_id])['InstanceStatuses']
    return len(statuses) > 0 and 'running' == statuses[0]['InstanceState']['Name']

def wait_for_all_to_run(instances):
    for instance in instances['Instances']:
        sys.stderr.write("Waiting for instance id=%s to start...\n" % (instance['InstanceId']))
        while not instance_running(instance):
            time.sleep(0.5)

def write_env_file(broker_ip, broker_port):
    username = os.environ['ctakes_umlsuser']
    password = os.environ['ctakes_umlspw']

    temp_file = tempfile.NamedTemporaryFile(mode='w')
    temp_file.write('%s=%s\n' % ('ctakes_umlsuser', username))
    temp_file.write('%s=%s\n' % ('ctakes_umlspw', password))
    temp_file.write('%s=%s\n' % ('broker_host', broker_ip))
    temp_file.write('%s=%s\n' % ('broker_port', broker_port))
    temp_file.flush()
    return temp_file

if __name__ == '__main__':
    main(sys.argv[1:])
