#!/bin/bash
# Requires the AWS CLI to be installed, 
# and an image to have been created that contains your containers, and 
# a script called /home/ubuntu/ctakes-docker/start.ae.containers.sh  
# that will start the containers such as a deidentification tool, a cTAKES pipeline,
# and a Writer that outputs the data produced by cTAKES.
# Set your image ID, key pair name, security group, and subnet ID below.

# First parameter is the number of AWS instances to launch
# Second parameter is optional - if any second parameter is given, a dry-run will be performed
dryRun=""
iType=m4.large
#
iId=ami-xxxxxxxx
key=YOURKEYNAME
sGroup=sg-xxxxxxxx
subnetId=subnet-xxxxxxxx


if [ "$1" = "" ]
then
  echo Number of instances not given. Not starting any instances.
  echo
  exit 1
fi

if [ "$3" != "" ]
then
  echo "Extranous third parameter given: \"$3\""
  echo
  exit 1
fi

if [ "$2" != "" ]
then
  echo
  echo "Doing a dry run .... because found a second parameter: $2"
  echo
dryRun=" --dry-run "
fi


echo "Launching $1 instances from image $iId using command:"

echo "aws ec2  run-instances  --image-id $iId  --count $1 --instance-type $iType --key-name $key --security-group-ids  $sgroup  $dryRun  --user-data '#!/bin/bash 
/home/ubuntu/ctakes-docker/start.ae.containers.sh' "

# Note that the user-data is a 2-line script, with the first line being the standard bash script header
aws ec2  run-instances  --image-id $iId  --count $1 --instance-type $iType --key-name $key --security-group-ids  $sgroup  $dryRun  --user-data '#!/bin/bash 
/home/ubuntu/ctakes-docker/start.ae.containers.sh' 

echo

# sleep 60    # You could set a sleep time here to allow the instances to have time to start up
