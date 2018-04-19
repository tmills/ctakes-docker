#!/usr/bin/env python3
import sys
import boto3

def main(args):
    if len(args) < 1:
        sys.stderr.write("One required argument: <instance id file>\n")
        sys.exit(-1)

    ec2 = boto3.resource('ec2')

    f = open(args[0], 'r')
    for line in f.readlines():
        inst_id = line.rstrip().split('=')[1]
        print("Stopping instance %s" % inst_id)
        instance = ec2.Instance(inst_id)
        instance.stop()


if __name__ == '__main__':
    main(sys.argv[1:])
