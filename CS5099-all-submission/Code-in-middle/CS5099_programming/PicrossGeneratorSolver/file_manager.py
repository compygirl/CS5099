# ####################################################################################
# ## This module file deals with all file processes (write, read, delete):
# ##  1. writes the files;
# ##
# ##  2. reads the files content in the line representation;
# ##
# ##  3. deletes the file;
# ##
# ##  4. delets the all unneeded files like '.infor', '.infor', '.minion' files;
# ##
# ##  5. creates instance file obtaining the content from tge instance_generator module.
# ####################################################################################

import os
from instance_generator import *


def write_param_file(param_directory, param_filename, content):
    complete_name = os.path.join(param_directory, param_filename)
    with open(complete_name, 'w') as g:
        g.write(content)

    print(param_filename + " file created successfully")


def read_file_content(directory, param_filename):
    fpath = os.path.join(directory, param_filename)
    from_file = open(fpath, 'r')
    all_lines = from_file.readlines()
    return all_lines


def delete_file(param_directory, param_filename):
    complete_name = os.path.join(param_directory, param_filename)
    os.remove(complete_name)


def delete_unneded_files(param_directory):
    for fil in os.listdir(param_directory):
        filename_arr = fil.split('.')
        # # deletes all other files except solution and param files.
        if len(filename_arr) != 4 and len(filename_arr) != 2:
            delete_file(param_directory, fil)


def create_instance_file(solution_board, param_directory, param_filename):
    content = create_content_of_inst_file(solution_board)
    write_param_file(param_directory, param_filename, content)
