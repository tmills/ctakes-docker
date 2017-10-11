#!/usr/bin/env python3
import sys

BROKER_AMI='ami-05dcf47e'
BROKER_MACHINE='m4.large'
BROKER_REPLICATION=1

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

def main(args):
    if len(args) < 2:
        sys.stderr.write("Arguments: <collection reader> <pipeline>\n")
        sys.exit(-1)

    ## Start the broker instance:
    broker_instance = start_instance(BROKER_AMI, BROKER_REPLICATION, BROKER_MACHINE)

    ## start the mist instance:
    mist_instance = start_instance(MIST_AMI, MIST_REPLICATION, MIST_MACHINE)

    ## start the ctakes instance:
    pipeline_instance = start_instance(PIPELINE_AMI, PIPELINE_REPLICATION, PIPELINE_MACHINE)

    ## start the i2b2 writer instance:
    writer_instance = start_instance(WRITER_AMI, WRITER_REPLICATION, WRITER_MACHINE)

if __name__ == "__main__":
    main(sys.argv[1:])


def start_instance(ami, count, inst_type)
    return ec2.create_instances(ImageId=ami,
                                MinCount=1,
                                MaxCount=count,
                                InstanceType=inst_type,
                                KeyName=key_pair)

# Boto 3
# Use the filter() method of the instances collection to retrieve
# all running EC2 instances.

def instance_running(inst):
    instances = ec2.instances.filter(
        Filters=[{'Name': inst.name, 'Values': ['running']}])
    return len(instances) > 0

#    for instance in instances:
#        print(instance.id, instance.instance_type)

if __name__ == '__main__':
    main(sys.argv[1:])
