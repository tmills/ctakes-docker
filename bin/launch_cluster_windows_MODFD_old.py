#!/usr/bin/env python2
# Launch a broker instance and one or more of each type of client instance.
# The client instances are for MIST, cTAKES, and a database writer
#
# Script takes one input parameter - the name of a configuration file.
#
# Changes for testing that should be undone when complete            # (ThisStringIsForGrepMatchingToRemoveThisLine)
#   prepended j- to instance names                                   # (ThisStringIsForGrepMatchingToRemoveThisLine)
#   changed command strings for testing in run_command_on_instances  # (ThisStringIsForGrepMatchingToRemoveThisLine)
#     to echo command to file instead of running command             # (ThisStringIsForGrepMatchingToRemoveThisLine) 
#   changed broker_port from 61616 to 80                             # (ThisStringIsForGrepMatchingToRemoveThisLine)     
#  
from __future__ import print_function   # use Python 3 print function, so uses of print will be compatible with Python 3.
import os
import sys
import tempfile      # Manages temporary files 
import time
import traceback
import datetime
from subprocess import call
import configparser  # manages configure file containing run parameters

import boto3         # Amazon web services SDK for python
import paramiko      # Python implementation of SSH
from paramiko.client import SSHClient 

ENV_FILE_NAME='env_file.txt'

BROKER_INSTANCE_NAME="NLP-Broker-Autostart"
MIST_INSTANCE_NAME="NLP-Mist-Autostart"
CTAKES_INSTANCE_NAME="NLP-Pipeline-Autostart"
WRITER_INSTANCE_NAME="NLP-Writer-Autostart"

# Prevent trying to start a large number of instances if there is a mistake in the config file
MAX_EXPECTED_REPLICATIONS=50       # Per type of client instance

BROKER_INSTANCE_NAME="tm-" + BROKER_INSTANCE_NAME  # TODO remove this before finalizing. (ThisStringIsForGrepMatchingToRemoveThisLine)
MIST_INSTANCE_NAME=  "tm-" + MIST_INSTANCE_NAME    # TODO remove this before finalizing. (ThisStringIsForGrepMatchingToRemoveThisLine)
CTAKES_INSTANCE_NAME="tm-" + CTAKES_INSTANCE_NAME  # TODO remove this before finalizing. (ThisStringIsForGrepMatchingToRemoveThisLine)
WRITER_INSTANCE_NAME="tm-" + WRITER_INSTANCE_NAME  # TODO remove this before finalizing. (ThisStringIsForGrepMatchingToRemoveThisLine)

USER_KEY_REQUIRED=True      
# USER_KEY_REQUIRED=False     # TODO When remove this, line above sets to True for public git repo (ThisStringIsForGrepMatchingToRemoveThisLine)
USER_KEY='ctakes_umlsuser'  # Needed if using cTAKES dictionary from sf.net or create your own using cTAKES GUI
PW_KEY='ctakes_umlspw'      # Needed if using cTAKES dictionary from sf.net or create your own using cTAKES GUI

broker_port=61616
### TODO remove the lines below for broker_port=80 before pushing to public git repo  # (ThisStringIsForGrepMatchingToRemoveThisLine)
### NOTE that broker_port is set twice to preserve the line with the typical 61616    # (ThisStringIsForGrepMatchingToRemoveThisLine)
###      and then to actually use 80 which we are using on GRIN for Precision Link    # (ThisStringIsForGrepMatchingToRemoveThisLine)
broker_port=80        # TODO   # (ThisStringIsForGrepMatchingToRemoveThisLine)

ec2_client = None
ec2_resource = None

start_time = datetime.datetime.now()
format = "%Y%m%d_%H.%M.%S.%f"                    # formatting of date and time in a way that fits filename character limitations
time_valid_for_fn = start_time.strftime(format)  # date and time formatted in a way that can be used within a filename 
log_details = False   # logs more completion messages and cleanup success messages; and some extra error information for some errors 
log_details = True    # TODO remove this before public git repo  # (ThisStringIsForGrepMatchingToRemoveThisLine)
broker_only = False   # for debugging when only want the broker launched 
broker_only = False    # TODO remove this before public git repo  # (ThisStringIsForGrepMatchingToRemoveThisLine)
skip_ec2_cmds = False  # TODO remove this before public git repo  # (ThisStringIsForGrepMatchingToRemoveThisLine)
stop_after_launch_broker = False # TODO remove this before public git repo  # (ThisStringIsForGrepMatchingToRemoveThisLine)

def main(args):

    print("Script start time is %s" % (start_time.isoformat()))

    if len(args) < 1:
        sys.stderr.write("Arguments: <config file>\n")
        sys.stderr.write("Example: python %s my_config_file.txt\n" % (__file__))
        sys.exit(-1)

    config_map = read_config(args[0])
    if config_map is None:
        sys.stderr.write("Unable to get any configuration options from %s\n", args[0])
        sys.exit(-1)

    if (len(args) > 1) and (args[1]=="stop"):     #  (ThisStringIsForGrepMatchingToRemoveThisLine)
        stop_after_launch_broker=True             # (ThisStringIsForGrepMatchingToRemoveThisLine)
        out("Will stop after launching broker.")  # (ThisStringIsForGrepMatchingToRemoveThisLine)
    else:                                         # (ThisStringIsForGrepMatchingToRemoveThisLine)
        stop_after_launch_broker = False          # (ThisStringIsForGrepMatchingToRemoveThisLine)

    ## Read in the configuration parameters
    BROKER_AMI = get_required(config_map, 'BROKER_AMI')
    BROKER_MACHINE = get_required(config_map, 'BROKER_MACHINE')
    BROKER_REPLICATION = 1   # always use 1 for now, haven't implemented more than one broker for the same run
    # Not using get_replication_value here because if BROKER_REPLICATION is not 1, we want to just exit.

    # Verify only one broker instance requested for this invocation of this script
    if BROKER_REPLICATION!=1:
        sys.stderr.write("This script currently only supports one (1) broker instance per invocation.\n")
        sys.stderr.write("If you'd like to run multiple brokers, you could split the input data into separate tables,\n")
        sys.stderr.write("create a separate config file for each table, and invoke this script once per config file.\n")
        sys.stderr.write("Exiting.\n")
        sys.exit(-1)

    # For now the minimum expected replications for everything below is 1 because right now 
    # we expect to always use MIST and always output to the database table with the writer 
    MIST_AMI = get_required(config_map, 'MIST_AMI')
    MIST_MACHINE = get_required(config_map, 'MIST_MACHINE')
    MIST_REPLICATION = get_replication_value(config_map, 'MIST_REPLICATION', 1, MAX_EXPECTED_REPLICATIONS)
    if (MIST_REPLICATION is None) or (MIST_REPLICATION < 0):
        sys.stderr.write("Exiting due to error getting %s.\n" % 'MIST_REPLICATION')
        sys.exit(-1)

    PIPELINE_AMI = get_required(config_map, 'PIPELINE_AMI')
    PIPELINE_MACHINE = get_required(config_map, 'PIPELINE_MACHINE')
    PIPELINE_REPLICATION = get_replication_value(config_map, 'PIPELINE_REPLICATION', 1, MAX_EXPECTED_REPLICATIONS)
    if (PIPELINE_REPLICATION is None) or (PIPELINE_REPLICATION < 0):          
        sys.stderr.write("Exiting due to error getting %s.\n" % 'PIPELINE_REPLICATION')
        sys.exit(-1)

    WRITER_AMI = get_required(config_map, 'WRITER_AMI')
    WRITER_MACHINE = get_required(config_map, 'WRITER_MACHINE')
    WRITER_REPLICATION = get_replication_value(config_map, 'WRITER_REPLICATION', 1, MAX_EXPECTED_REPLICATIONS)
    if (WRITER_REPLICATION is None) or (WRITER_REPLICATION < 0):          
        sys.stderr.write("Exiting due to error getting %s.\n" % 'WRITER_REPLICATION')
        sys.exit(-1)

    project = get_required(config_map, 'project')

    input_table_name = get_required(config_map, 'input_table_name')
    output_table_name = get_required(config_map, 'output_table_name')

    key_file = get_required(config_map, 'key_file')

    if USER_KEY in config_map.keys() and config_map[USER_KEY]!='':
        umls_user = config_map[USER_KEY]
    elif USER_KEY in os.environ.keys():
        umls_user = os.environ[USER_KEY]
    else: 
        umls_user = ""

    if PW_KEY in config_map.keys() and config_map[PW_KEY]!='':
        umls_pwd = config_map[PW_KEY]
    elif PW_KEY in os.environ.keys():
        umls_pwd = os.environ[PW_KEY]
    else:
        umls_pwd = ""

    if USER_KEY_REQUIRED:
      if umls_user == "":
          sys.stderr.write("The environment variable %s must be defined for this script\n" % (USER_KEY))
          sys.stderr.write("or the configuration file must set \n" % (USER_KEY))
          sys.exit(-1)
      if umls_pwd == "":
          sys.stderr.write("The environment variable %s must be defined for this script\n" % (PW_KEY))
          sys.stderr.write("or the configuration file must set \n" % (PW_KEY))
          sys.exit(-1)


    global ec2_client
    global ec2_resource
    ec2_client = boto3.client('ec2')
    ec2_resource = boto3.resource('ec2')

    # Include the date and time in the log filename to preserve history if multiple runs
    out_fn = get_required(config_map, 'id_file_prefix') + '.' + time_valid_for_fn + '.' + get_required(config_map, 'id_file_suffix')
    fout = open(out_fn, 'w')


    ## Start the instances, but don't start the containers yet
    # Start the broker instance
    try:
        broker_instances = start_instances(BROKER_AMI, BROKER_REPLICATION, BROKER_MACHINE, config_map, "localhost", fout, BROKER_INSTANCE_NAME, project)
    except (Exception, KeyboardInterrupt):   # try to close file before allowing Control-C to halt the script
        close_ignore_exception(fout)
        raise
    if broker_instances is None:
        sys.stderr.write('Could not start broker. Exiting.\n')
        close_ignore_exception(fout)
        sys.exit(-1)

    if stop_after_launch_broker:
        out("STOPPING AFTER LAUNCHING BROKER") ; sys.exit(-2)  #  JJM (ThisStringIsForGrepMatchingToRemoveThisLine)

    # Get the broker IP address which the other instances need for registering with the broker.
    # This next bit assumes just one broker instance, which was verified above.
    broker_id = get_instance_id(get_instances(broker_instances)[0])
    out("Get IP address for broker instance %s" % broker_id)
    broker_private_ip = get_private_ip(broker_id)
    print()
    if broker_private_ip is None:
        sys.stderr.write('Could not get IP address for broker. Exiting.\n')
        sys.stderr.flush()
        close_ignore_exception(fout)
        sys.exit(-1)
    

    ## Write the information about the broker that the other containers will need to a temp env_file
    env_file = write_env_file(broker_private_ip, broker_port, umls_user, umls_pwd)
    broker_ip = broker_private_ip

    ## Start the rest of the instances before starting the containers or running a command on the broker. 
    ## The broker instance needs time for its networking to finish starting up even after it gets to "running" state anyway.

    if broker_only==True:   # provide an easy way to skip starting the client instances for debugging
        out('Not starting other instances because broker_only = %s\n' % broker_only)    # (ThisStringIsForGrepMatchingToRemoveThisLine)
        mist_instances = None
        pipeline_instances = None
        writer_instances = None
    else:
        try: 
            ## start the mist instance(s):
            mist_instances = start_instances(MIST_AMI, MIST_REPLICATION, MIST_MACHINE, config_map, broker_ip, fout, MIST_INSTANCE_NAME, project)

            ## start the ctakes instance(s):
            pipeline_instances = start_instances(PIPELINE_AMI, PIPELINE_REPLICATION, PIPELINE_MACHINE, config_map, broker_ip, fout, CTAKES_INSTANCE_NAME, project)

            ## start the database writer instance(s):
            writer_instances = start_instances(WRITER_AMI, WRITER_REPLICATION, WRITER_MACHINE, config_map, broker_ip, fout, WRITER_INSTANCE_NAME, project)

        except (Exception, KeyboardInterrupt):
            close_ignore_exception(fout) 
            close_ignore_exception(env_file) 
            raise

    # Done starting instances so done logging to the file containing the IDs of the instances we started.   
    close_ignore_exception(fout) 

    ### Now that the instances have been started, start the container on each instance
    try:

        start_containers(broker_instances, mist_instances, pipeline_instances, writer_instances, env_file, key_file, output_table_name)

    except (KeyboardInterrupt):

        close_ignore_exception(env_file)
        sys.stderr.write("Program was interrupted while starting containers.\n");
        sys.stderr.write("Exiting.")
        sys.exit(-1)

    except Exception as e:
        close_ignore_exception(env_file)
        sys.stderr.write("Exception while starting containers.\n");
        traceback.print_exc() #sys.stderr.write("Exception: %s\n" % e)
        sys.stderr.write("Exiting.\n")
        sys.exit(-1)

    ### Have the broker start processing data
    try:

        ## run the command on the broker that processes the input from a data base table
        cmd = "ctakes-docker/process.db.sh %s" % input_table_name   # TODO fill in the correct command here
        #cmd = "(date; echo ctakes-docker/process.db.sh %s) >> ctakes-docker/logged.run.commands.txt ; date ; hostname ; "  % input_table_name   # TODO remove this when ready to run real cmd  # (ThisStringIsForGrepMatchingToRemoveThisLine)
        run_command_on_instances(broker_instances, cmd, env_file, key_file, 25)
        print()
 
    except (KeyboardInterrupt):

        close_ignore_exception(env_file)
        sys.stderr.write("Program was interrupted while running command on broker.\n");
        sys.stderr.write("Exiting.")
        sys.exit(-1)

    except Exception as e:

        close_ignore_exception(env_file)
        sys.stderr.write("Exception while running command on broker.\n");
        sys.stderr.write("Exception: %s\n" % e)
        sys.stderr.write("Exiting.\n")
        sys.exit(-1)

    close_ignore_exception(env_file)

def get_required(config_map, key):
    value = get_value(config_map, key)

    if value is None:
        sys.stderr.write('Required configuration option %s not found.\n' % key)
        sys.stderr.write('value %s.\n' % value)
        sys.stderr.write('Configuration %s.\n' % config_map)
        sys.stderr.write('config_map[%s] = %s\n' % (key, config_map[key]))
        sys.stderr.write('config_map.keys() = %s\n' % (config_map.keys()))
 
        sys.exit(-1)
    if len(value.strip())> 0:
        return value

    sys.stderr.write('Value not set for required configuration option %s.\n' % key)
    sys.exit(-1)
    
def get_value(config_map, key):
    """ This does not do truly validate the config value but it at least makes sure it is 
        present and is not an empty string and not just all whitespace 
    """
    try:
        return config_map[key]
    except: 
        return None

def get_replication_value(config_map, key, min_expected, max_expected):
    """ Get the replication value for key from the config_map and do some
        basic validation so we don't try to start up a very large number of instances
        If a component is required, set pass at least 1 for min_expected to ensure
        at least one instance of that type gets launched         
    """
    if min_expected > max_expected:
        out("Warning: Bad range for %s. minimum (%s) should not be greater than max (%s)\n", (key, min_expected, max_expected))
    if min_expected < 0:
        out("Warning: Bad range for %s. minimum (%s) should be at least 0\n", (key, min_expected))

    #if not(key in config_map.keys()):
    #    return None
    try:
        value = int(config_map[key])
    except ValueError:
        return None
    except Exception:
        sys.stderr.write("Error reading %s from the config file %s\n" % (key, config_file))
        sys.stderr.flush()
        return None

    if value < min_expected:
        out("Warning: Replication value %s is less than minimum of %s for %s\n", (value, min_expected, key))
        if value >= 0:
            value = min_expected  # TODO do we really want to force at least one, or just stop... should stop for now at least
    elif value > max_expected:
        out("Warning: Replication value %s greater than the limit this script imposes of %s for %s\n", (value, max_expected, key))
        out("         Using %s\n", (max_expected))
        value = max_expected
    return value


def get_instance_hostname(instance):
    """ 
        :param instance: an Instance, not an instance_id
    """
    instance.reload()
    hostname = instance.public_dns_name

def get_private_ip(instance_id):
    """ 
        :param str instance_id: an Instance's id identifier.
    """
    global ec2_resource
    try:
        instance = ec2_resource.Instance(instance_id)
        for x in range(1,20):
            instance.reload()
            private_ip = instance.private_ip_address
            if (private_ip!=None and private_ip!=""):
                out("\nIP address is %s" % private_ip)
                break
            out(".")  # only print a dot if needs more than one try
        else:
            out("\nDidn't get IP address within the allotted number of tries.\n")
            return None
    except KeyboardInterrupt:   # If Control-C etc, allow caller to cleanup before halting the script
        return None
    except Exception as e:
        out("Exception %s\n" % e)
        return None

    return private_ip

def out(msg):
    print(msg, end="")
    sys.stdout.flush() 

def start_containers(broker_instances, mist_instances, pipeline_instances, writer_instances, env_file, key_file, output_table_name):
        ## Start the broker container, which includes copying the file with environment variables to the broker instance
        cmd = "ctakes-docker/bin/runBrokerContainer.sh"
        #cmd = "(date; echo ctakes-docker/bin/runBrokerContainer.sh) >> ctakes-docker/logged.run.commands.txt ; date ; hostname ; "   # TODO remove this when ready to run real cmd  # (ThisStringIsForGrepMatchingToRemoveThisLine)
        run_command_on_instances(broker_instances, cmd, env_file, key_file, 0)

        if broker_only==True:
            out('Not actually starting containers because broker_only = %s\n' % broker_only)    # (ThisStringIsForGrepMatchingToRemoveThisLine)
            return

        if mist_instances is None:
            if log_details:
                out("No MIST instances were started, skipping starting the MIST container.\n")
        else:
            ## start the mist AE on the mist instance(s):
            cmd = "cd ctakes-docker; ./bin/runMistContainer.sh"
            #cmd = "(date; echo ctakes-docker/bin/runMistContainer.sh ) >> ctakes-docker/logged.run.commands.txt ; date ; hostname ; "   # TODO remove this when ready to run real cmd  # (ThisStringIsForGrepMatchingToRemoveThisLine)
            run_command_on_instances(mist_instances, cmd, env_file, key_file, 2)
            print()

        if pipeline_instances is None:
            if log_details:
                out("No cTAKES instances were started, skipping starting the cTAKES container.\n")
        else:
            ## start the ctakes pipeline on the ctakes instance(s):
            cmd = "cd ctakes-docker; ./bin/runPipelineContainer.sh"
            #cmd = "(date; echo ctakes-docker/bin/runPipelineContainer.sh ) >> ctakes-docker/logged.run.commands.txt ; date ; hostname ; "   # TODO remove this when ready to run real cmd  # (ThisStringIsForGrepMatchingToRemoveThisLine)
            run_command_on_instances(pipeline_instances, cmd, env_file, key_file, 2)
            print()

        if writer_instances is None:
            if log_details:
                out("No writer instances were started, skipping starting the writer container.\n")
        else:
            ## start the database writer container on the writer instance(s) and set the table name on the instance
            cmd = "cd ctakes-docker; ./bin/runWriterContainer.sh %s" % output_table_name
            #cmd = "(date; echo ctakes-docker/bin/runWriterContainer.sh %s) >> ctakes-docker/logged.run.commands.txt ; date ; hostname ; " % output_table_name   # TODO remove this when ready to run real cmd  # (ThisStringIsForGrepMatchingToRemoveThisLine)
            run_command_on_instances(writer_instances, cmd, env_file, key_file, 2)
            print()
            # Now that the database container is started, copy the updated xml file with the table name from 
            # the instance to the container and start the writer async service to process requests
            cmd = "cd ctakes-docker; ./bin/runWriterOnWriterContainer.sh %s" % output_table_name
            run_command_on_instances(writer_instances, cmd, env_file, key_file, 1)


def start_instances(ami, count, instance_type, config, broker_ip, outfile, name="Unnamed Instance", project="Unnamed Project"):
    """ Launch 'count' instances of type (size) instance_type, wait for the instances to 
        get to "running" state and log the id of each instance to outfile

        :param ami: image used in launching the instances
        :param int count: the number of instances to launch
        :param str instance_type: for example m4.large
        :param config: 
        :param str broker_ip: IP address of broker
        :param outfile: the file to log the instance IDs to
        :param str name: The value to assign to the Name tag for each instance
        :param str project: The value to assign to the Project tag for each instance
    """
    max_instances_allowed_per_call = 50
    if count<1 :
        return None
    if count > max_instances_allowed_per_call :
        out("Check for input or config error - requested %s instances for image %s.\n" % (count, ami))
        count = max_instances_allowed_per_call
        out("Only launching %s instances.\n" % count)
    instances = launch_instances(ami, count, instance_type, config, broker_ip, name, project)
    log_instances(instances, outfile)
    completed = wait_for_instances_to_run(instances)
    if not completed:
        return None

    return instances


def read_config(config_file):
    config = configparser.ConfigParser()
    try:
        config.read(config_file)
        config_map = config['default']
    except Exception:
        sys.stderr.write("Error reading the config file or no default configuration within it: %s\n" % config_file)
        sys.stderr.flush()
        return None

    return config_map

def log_instances(instances, fout):
    for instance in get_instances(instances):
        instance_id = get_instance_id(instance)
        fout.write('instance_id=%s\n' % (instance_id))
        fout.flush()

def run_command_on_instances(instances, command, env_file, key_file, wait_time):
    global ec2_resource

    for instance in get_instances(instances):
        instance_id = get_instance_id(instance)   # such as i-00fda75aa93215ce3 
        out("Preparing to run command on instance %s\n" % (instance_id))
        instance_obj = ec2_resource.Instance(instance_id)
        instance_obj.reload()
        inst_ip = instance_obj.public_dns_name
         
        if (wait_time > 0):
            out("Current time is %s\n" % datetime.datetime.now().strftime(format))
            out("  waiting for %s seconds for the instance to start up networking programs before trying ssh." % wait_time)

            for x in xrange(1, wait_time):   # now that the instance is running, delay to let ssh get started before we try even the first time
                time.sleep(1)
                out('.')
            print()

        conn_timeout=20         # This is the first-time connection timeout, we increase it below if first time fails
        overall_timeout = 299   # Don't loop forever, don't do any more attempts after this amount of time passes

        out("Opening ssh connection:\n")
        timeout_start = time.time()
        connected=False
        while time.time() < timeout_start + overall_timeout:
          try: 
            ssh = SSHClient()
            ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
            out("Attempting to connect to remote host %s \n" % (inst_ip))
            out("  with timeout %s seconds using keyfile %s\n" % (conn_timeout, key_file))
            ssh.connect(inst_ip, username='ubuntu', timeout=conn_timeout, key_filename=key_file)
            connected = True
            out("SSH connection opened.\n")
            break
          except (KeyboardInterrupt, SystemExit):
            close_ignore_exception(ssh)
            raise
          except paramiko.ssh_exception.NoValidConnectionsError as e:
            sys.stderr.write('SSH transport is not ready.\n')
            time.sleep(15)    # Wait 15 seconds before next attempt
            conn_timeout=60   # Connection timeout 40 seconds here worked when using cygwin over VPN, so boost to 60 for busier times
            close_ignore_exception(ssh)
            continue
          except Exception as e:
            sys.stderr.write("Connection attempt failed.\n")
            sys.stderr.write("  while trying to connect, received exception: %s\n" % e)
            if log_details:
                if hasattr(e, 'errno'):
                    sys.stderr.write("  e.errno: %s\n" % (e.errno))
                if hasattr(e, 'strerror'):
                    sys.stderr.write("  e.strerror: %s\n" % (e.strerror))
                try:
                    sys.stderr.write("Unexpected error, sys.exc_info()[0] = %s\n" % (sys.exc_info()[0]))
                except:
                    sys.stderr.write("Unable to inspect sys.exc_info()[0].\n" )
            close_ignore_exception(ssh)
            delay = 15
            sys.stderr.write("Waiting %s seconds before retrying....\n" % delay
)
            time.sleep(delay)    # Wait 15 seconds before next attempt
            conn_timeout=60   # Connection timeout 40 seconds here worked when using cygwin over VPN, so boost to 60 for busier times

        if not connected:
          raise Exception("Failed to connect to %s" % (inst_ip))

        out("Copying %s to host from temporary file %s \n" % (ENV_FILE_NAME, env_file.name))
        # Note that the output of the scp call will be the name of the temporary file; without the above
        # statement being output, the file name such as tmpq_noRK will appear mysterious
        # Use StrictHostKeyChecking=off to avoid "The authenticity of host 'ec2-xxx-yyy-zzz-www.compute-1.amazonaws.com (aaa.bbb.ccc.ddd)' can't be established."
        call(['scp', '-i', key_file, '-o', 'StrictHostKeyChecking=off', env_file.name, 'ubuntu@%s:/home/ubuntu/ctakes-docker/%s' % (inst_ip, ENV_FILE_NAME)]) 
        time.sleep(4)
        if log_details:
            out("Done waiting for file %s to be copied to host %s\n" % (ENV_FILE_NAME, inst_ip))
    
        #command='ls -la'
        #command='grep env *'
        out("Running the following command on a remote host %s:  %s\n" % (inst_ip, command))
        out("  Current time is %s\n" % (datetime.datetime.now().strftime(format)))
        out("  Command's output won't be displayed until after command finishes.\n")
        stdin, stdout, stderr = ssh.exec_command(command)
        stdin.flush()
        output = stdout.read().splitlines()
        error = stderr.read().splitlines()
        if len(output)!=-1: # use 0 to hide output when output is empty
            sys.stderr.write('Stdout from run command:\n')
            for line in output:
                sys.stderr.write('%s\n' % line) 
            sys.stderr.flush()
        if len(error)!=0:
            sys.stderr.write('Stderr from run command:\n')
            for line in error:
                sys.stderr.write('%s\n' % line) 
            sys.stderr.flush()
        out("Closing SSH connection.\n")
        close_ignore_exception(ssh)

def close_ignore_exception(closeable):
    try:

        #out('  Closing %s\n' % closeable)
        if hasattr(closeable, "closed") and (closeable.closed):
            # don't need to try closing, already closed
            return

        closeable.close()

        if hasattr(closeable, "name"):
            out('  Closed: %s\n' % closeable.name)
        else:
            out('  Closed: %s\n' % closeable)

    except Exception:   # Consider handle double Control-C better

        sys.stderr.write('Closing failed (might not have been needed): %s\n' % (closeable.__class__.__name__))  


def launch_instances(ami, count, instance_type, config, broker_ip, tag_name="NLP_Auto_Start", project="Unnamed Project"):
    global ec2_client

    if count < 1:
        if log_details:
            out("REPLICATION value is %s, not launching any %s instances.\n" % (count, tag_name))
        return None
                       # (ThisStringIsForGrepMatchingToRemoveThisLine)
    if skip_ec2_cmds:  # (ThisStringIsForGrepMatchingToRemoveThisLine)
        out('skip_ec2_cmds = %s\n' % skip_ec2_cmds)    # (ThisStringIsForGrepMatchingToRemoveThisLine)
        return None    # (ThisStringIsForGrepMatchingToRemoveThisLine)

    instances = ec2_client.run_instances(ImageId=ami,
                                    MinCount=1,
                                    MaxCount=count,
                                    InstanceType=instance_type,
                                    KeyName=config['key_pair'],
                                    TagSpecifications=[
                                        {'ResourceType':'instance',
                                         'Tags':[
                                            {'Key':'Name', 'Value':tag_name},
                                            {'Key':'Project', 'Value':project},
                                            {'Key':'Script start', 'Value':start_time.isoformat()},
                                            {'Key':'Broker IP', 'Value':broker_ip}
                                            ]
                                        }],
                                    SubnetId=config['subnet_id'],
                                    SecurityGroupIds=config['security_groups'].split(',')
                                 )
    return instances

# Boto 3
# Use the filter() method of the instances collection to retrieve all running EC2 instances.

# encapsulate dependence on ec2 / definition of is running
def get_running_state():
    return 'running'

def is_running_state(instance_state):
    return (instance_state==get_running_state())

# return empty string or the InstanceState of the ec2 instance
def get_instance_state(instance):
    global ec2_client
    instance_id = get_instance_id(instance)
    statuses = ec2_client.describe_instance_status(InstanceIds=[instance_id])['InstanceStatuses']
    if (statuses is None) or (len(statuses)==0):
        return ""
    return statuses[0]['InstanceState']['Name']

def get_instances(instances):
    return instances['Instances']

def get_instance_id(instance):
    return instance['InstanceId']

def wait_for_instances_to_run(instances):
    """ Wait for the instances to get to "running" state 
        return True if instance gets to running state before timeout
    """
    for instance in get_instances(instances):
        out("Checking repeatedly if instance %s has entered '%s' state. This should take less than 2 minutes.\n" % (get_instance_id(instance), get_running_state()));
        out("  Use Control-C to stop if needed ....")
        # TODO How well does Control-C end this process - does it end it cleanly?
        state = get_instance_state(instance)
        MAX_TRIES = 999999 # normally try so many times it appears it doesn't stop on its own unless successful
        # MAX_TRIES = 2      # for testing error paths
        count = 0
        while (count < MAX_TRIES) and (not is_running_state(state)):
            try:
                out(".")
                count+=1
                time.sleep(0.5)
                previous_state = state
                state = get_instance_state(instance)
                if previous_state!=state:
                    out("\nInstance has entered '%s' state.\n" % state)
            except KeyboardInterrupt:   # If Control-C etc, allow calling function to cleanup before halting the script
                out("\n\nInterrupted program while waiting for instances to run.\n\n")
                raise
            except Exception as e:
                sys.stderr.write("Exception: %s\n" % e)
                if hasattr(e, 'errno'):
                    sys.stderr.write("  e.errno: %s\n" % (e.errno))
                if hasattr(e, 'strerror'):
                    sys.stderr.write("  e.strerror: %s\n" % (e.strerror))
                try:
                    sys.stderr.write("Unexpected error, sys.exc_info()[0] = %s\n" % (sys.exc_info()[0]))
                except:
                    sys.stderr.write("Unable to inspect sys.exc_info()[0].\n" )
                raise

        if is_running_state(state):
            out("Instance %s started.\n" % (get_instance_id(instance)));    # such as i-00fda75aa93215ce3
            return True;
        else: 
            out("\nInstance %s in state '%s', waiting for it to get to '%s' state has timed out.\n" % (get_instance_id(instance), state, get_running_state()));
            return False;


def write_env_file(broker_ip, broker_port, umls_user="", umls_pass=""):
    """ Write the env_file information to a temporary file
        If there is any error, return None; 
        otherwise return the temp file
    """
    try:
        temp_file = tempfile.NamedTemporaryFile(mode='w')
        if umls_user!=None and umls_user!="":
            temp_file.write('%s=%s\n' % ('ctakes_umlsuser', umls_user))
        if umls_pass!=None and umls_pass!="":
            temp_file.write('%s=%s\n' % ('ctakes_umlspw', umls_pass))
        temp_file.write('%s=%s\n' % ('broker_host', broker_ip))
        temp_file.write('%s=%s\n' % ('broker_port', broker_port))
        temp_file.flush()
        return temp_file
    except KeyboardInterrupt:   # If Control-C etc, allow calling function to cleanup before halting the script
        close_ignore_exception(temp_file)
        return None
    except Exception as e:
        sys.stderr.write("Exception %s\n" % e)
        close_ignore_exception(temp_file)
        return None
    return None

if __name__ == '__main__':

    try:
        main(sys.argv[1:])
    except KeyboardInterrupt:   # If Control-C etc, give more friendly message than a Traceback
        sys.stderr.write("\nProgram was halted early by keyboard interrupt.\n"); sys.stderr.flush()
    except Exception as e:
        sys.stderr.write("Exception: %s\n" % e)
        if hasattr(e, 'errno'):
            sys.stderr.write("  e.errno: %s\n" % (e.errno))
        else:
            sys.stderr.write("  No errno attribute to display.\n" )
        if hasattr(e, 'strerror'):
            sys.stderr.write("e.strerror: %s\n" % (e.strerror))
        else:
            sys.stderr.write("  No strerror attribute to display.\n" )
        try:
            sys.stderr.write("Unexpected error, sys.exc_info()[0] = %s\n" % (sys.exc_info()[0]))
        except:
            sys.stderr.write("Unable to inspect sys.exc_info()[0].\n" )
            
        sys.stderr.flush()
        raise

