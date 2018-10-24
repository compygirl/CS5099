# ####################################################################################
# ## This module file deals with all file processes (write, read, delete):
# ##  1. write_param_file: writes the files;
# ##
# ##  2. read_file_content: reads the files content in the line representation;
# ##
# ##  3. delete_file: deletes the file;
# ##
# ##  4. delete_unneded_files: deletes the all unneeded files like '.infor',
# ##     '.infor', '.minion' files;
# ##
# ##  5. create_instance_file: creates instance file obtaining the content from
# ##     the instance_generator module.
# ##
# ##  6. create_csv_file_dict: creates the csv file of the dictionaty of stat
# ##     info about the instance and adds headers to the columns of the csv
# ##     file + plus adds the Filename folumn to know which instance this info
# ##     belongs.
# ##
# ##  7. add_values_to_csv: if the file already exist then it just opens it and
# ##     adds a new information to it.
# ##
# ##  8. check_file_exist: checks whether the file exists alredy.
# ####################################################################################

import os
import csv
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
        if len(filename_arr) != 2:
            delete_file(param_directory, fil)


def create_instance_file(solution_board, param_directory, param_filename):
    content = create_content_of_inst_file(solution_board)
    write_param_file(param_directory, param_filename, content)


def create_csv_file_dict(directory, csvfile, data):
    fpath = os.path.join(directory, csvfile)
    header = []
    header = data.keys()
    header.insert(0, "Filenames")
    writer = csv.writer(open(fpath, "w"))
    writer.writerow(header)


def add_values_to_csv(directory, csvfile, data, param_filename):
    fpath = os.path.join(directory, csvfile)
    fd = open(fpath, 'a')
    row = []
    row = data.values()
    row.insert(0, param_filename)
    writer = csv.writer(open(fpath, 'a'))
    writer.writerow(row)


def check_file_exist(directory, filename):
    fpath = os.path.join(directory, filename)
    return os.path.isfile(fpath)
