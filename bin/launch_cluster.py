#!/usr/bin/env python3
import os
import sys
import tempfile
import time
from subprocess import call

import boto3
from paramiko.client import SSHClient

BROKER_AMI='ami-05dcf47e'
BROKER_MACHINE='m4.large'

MIST_AMI='ami-05dcf47e'
MIST_MACHINE='m4.large'
MIST_REPLICATION=1

PIPELINE_AMI=''
PIPELINE_MACHINE='m4.large'
PIPELINE_REPLICATION=2

WRITER_AMI=''
WRITER_MACHINE='m4.large'
WRITER_REPLICATION=1

key_pair='nlplab'
key_file='/home/tmill/.ssh/nlplab.pem'

broker_port=61616

ec2 = None

def main(args):
    if len(args) < 2:
        sys.stderr.write("Arguments: <collection reader> <pipeline>\n")
        sys.exit(-1)

    global ec2

    ec2 = boto3.client('ec2')
    ec2r = boto3.resource('ec2')

    ## Start the broker instance:
    broker_instances = start_instances(BROKER_AMI, 1, BROKER_MACHINE)
    wait_for_all_to_run(broker_instances)
    broker_instance = ec2r.Instance(broker_instances['Instances'][0]['InstanceId'])
    broker_instance.reload()
    broker_ip = broker_instance.public_ip_address
    if broker_ip is None:
        raise Exception('Could not get IP address for broker')

    env_file = write_env_file(broker_ip, broker_port)
    call(['scp', '-i', key_file, env_file.name, '%s:/home/ubuntu/ctakes-docker/env_file.txt' % broker_ip])

    ssh = SSHClient()
    ssh.connect(broker_ip, username='ubuntu', key_filename=key_file)
    stdin, stdout, stderr = ssh.exec_command('ctakes-docker/bin/runBrokerContainer.sh')
    stdin.flush()
    output = stdout.read().splitlines()
    print('Broker startup output:')
    print(output)
    ssh.close()

    ## start the mist instance(s):
    mist_instances = start_instances(MIST_AMI, MIST_REPLICATION, MIST_MACHINE)
    wait_for_all_to_run(mist_instances)
    for instance in mist_instances['Instances']:
        mist_instance = ec2r.Instance(instance['InstanceId'])
        mist_instance.reload()
        mist_ip = mist_instance.public_ip_address
        call(['scp', '-i', key_file, env_file.name, '%s:/home/ubuntu/ctakes-docker/env_file.txt' % mist_ip])
        ssh.connect(mist_ip, username='ubuntu', key_filename=key_file)
        stdin, stdout, stderr = ssh.exec_command('ctakes-docker/bin/runMistContainer.sh')
        stdin.flush()
        output = stdout.read().splitlines()
        print('Broker startup output:')
        print(output)
        ssh.close()

    ## start the ctakes instance:
    pipeline_instances = start_instances(PIPELINE_AMI, PIPELINE_REPLICATION, PIPELINE_MACHINE)
    wait_for_all_to_run(pipeline_instances)

    ## start the i2b2 writer instance:
    writer_instances = start_instances(WRITER_AMI, WRITER_REPLICATION, WRITER_MACHINE)
    wait_for_all_to_run(writer_instances)

    env_file.close()

def start_instances(ami, count, inst_type):
    global ec2
    instances = ec2.run_instances(ImageId=ami,
                                MinCount=1,
                                MaxCount=count,
                                InstanceType=inst_type,
                                KeyName=key_pair)
    for instance in instances['Instances']:
        sys.stderr.write("Starting instance id=%s\n" % (instance['InstanceId']))
    return instances

# Boto 3
# Use the filter() method of the instances collection to retrieve
# all running EC2 instances.
def instance_running(inst):
    global ec2

    inst_id = inst['InstanceId']
    return 'running' == ec2.describe_instance_status(InstanceIds=[inst_id])
                            ['InstanceStatuses'][0]['InstanceState']['Name']

def wait_for_all_to_run(instances):
    for instance in instances['Instances']:
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
