#!/usr/bin/env python3
import sys

BROKER_AMI='ami-05dcf47e'
BROKER_MACHINE='m4.large'
BROKER_REPLICATION=1

MIST_AMI='ami-05dcf47e'
MIST_MACHINE='m4.large'
MIST_REPLICATION=1

key_pair='nlplab'

def main(args):
    if len(args) < 4:
        sys.stderr.write("Arguments: <collection reader>")

    ## Start the broker instance:
    broker_instance = start_instance(BROKER_AMI, BROKER_REPLICATION, BROKER_MACHINE)

    ## start the mist instance:

    ## start the ctakes instance:

    ## start the i2b2 writer instance:

if __name__ == "__main__":
    main(sys.argv[1:])


def start_instance(ami, count, inst_type)
    return ec2.create_instances(ImageId=ami,
                                MinCount=1,
                                MaxCount=count,
                                InstanceType=inst_type,
                                KeyName=key_pair)
