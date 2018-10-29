# +-----------------------------------------------------------------------------+
# |Python 2.7.13
# |
# |Author:           ID:090017799
# |
# |Module Name:      tester
# |Module Purpose:   test a program
# |Input:            images from the image directory
# |Output:           instances for the sovile row,
# |                  which runs and produces the solutions
# +-----------------------------------------------------------------------------+

from image_processor import *
from matrix_processor import *
from instance_generator import *
from game_analyser import *
from file_manager import *
from single_solution import *

from random_generator import *
from constants import *

import random
import string
import os
import re

import numpy as np


def test_image_processor():
    iterate_through_images('imagesInstances', "paramFiles")
    delete_unneded_files("paramFiles")


def main_executor(solution_board, image_filename, param_directory, image, EPRIME_FILE):
    print_matrix(solution_board)
    param_filename = transfer_imagename_to_param(image_filename)
    create_instance_file(solution_board, param_directory, param_filename)
    execute_savileRow(EPRIME_FILE, param_directory, param_filename)
    print_info(param_directory, param_filename)


def iterate_through_images(image_directory, param_directory):
    counter = 0
    for image_filename in os.listdir(image_directory):
        if image_filename.endswith(".jpg") or image_filename.endswith(".png"):
            print("=" * 80)
            img = read_image(image_directory, image_filename)
            solution_board = create_solution_board(img)
            print("SOLUTION BOARD of " + image_filename)
            # main_executor(solution_board, image_filename, param_directory, img, EPRIME_FILE)
            print_matrix(solution_board)
            param_filename = transfer_imagename_to_param(image_filename)
            create_instance_file(solution_board, param_directory, param_filename)
            execute_savileRow(EPRIME_FILE, param_directory, param_filename)
            print_info(param_directory, param_filename)
            if info_dict['SolverSolutionsFound'] > 1:
                print("=" * 80)
                print("MORE SOLUTIONS (>1):")
                print("=" * 80)
                print("Changing pixels:")
                diff_matrix = compare_two_solutions(param_directory, param_filename, solution_board)
                print_matrix(diff_matrix)

                updated_sol_board = update_instances(solution_board, diff_matrix, param_directory, image_filename)

            counter += 1
    print ('{} {} {}'.format("Overall ", counter, ".param files were created"))


def test_random_instances():
    # EPRIME_FILE = "picross_solver.eprime"

    num_files = 6  # 100
    param_directory = "randomInstances"
    for i in range(0, num_files):
        param_filename = "random" + str(i) + ".param"
        print(param_filename + ":")
        random_sol_board = get_random_sol_board()
        print('{} {}'.format("rows:", len(random_sol_board)))
        print('{} {}'.format("cols:", len(random_sol_board[0])))
        print_matrix(random_sol_board)
        content_of_file = convert_matrix_to_print_version(random_sol_board)
        # content_sol_board = convert_matrix_to_s(random_sol_board)
        write_param_file(param_directory, "random " + str(i) + "board", content_of_file)
        create_instance_file(random_sol_board, param_directory, param_filename)
        print('\n')  # change later
        execute_savileRow(EPRIME_FILE, param_directory, param_filename)
        print('\n')  # change later

        info_dict = get_info(param_directory, param_filename + ".info")
        print("{: >20} {: >20}: {: >20}".format("Num of Solutions: ", param_filename, info_dict['SolverSolutionsFound']))
        print("{: >20} {: >20}: {: >20}".format("Num of Nodes:", param_filename, info_dict['SolverNodes']))
        print("{: >20} {: >20}: {: >20}".format("Solver Total Time:", param_filename, info_dict['SolverTotalTime']))
        print("{: >20} {: >20}: {: >20}".format("Solve Time:", param_filename, info_dict['SolverSolveTime']))
        print("{: >20} {: >20}: {: >20}".format("SavileRow Total time:", param_filename, info_dict['SavileRowTotalTime']))

        if info_dict['SolverSolutionsFound']>1 :
            print("="*80)
            diff_matrix = compare_two_solutions (param_directory, param_filename, random_sol_board)
            print("DIFF:")
            print_matrix(diff_matrix)
            counterr = compare_with_original(random_sol_board, diff_matrix)
            print('{}: {}'.format("COUNT in TEST", counterr))
            update_pfilename = "random"+str(i)
            updated_sol_board = update_instances(random_sol_board, diff_matrix, param_directory, update_pfilename)
            print("{: >20} {: >20}: {: >20}".format("Num of Solutions: ", param_filename, info_dict['SolverSolutionsFound']))
            print("{: >20} {: >20}: {: >20}".format("Num of Nodes:", param_filename, info_dict['SolverNodes']))
            print("{: >20} {: >20}: {: >20}".format("Solver Total Time:", param_filename, info_dict['SolverTotalTime']))
            print("{: >20} {: >20}: {: >20}".format("Solve Time:", param_filename, info_dict['SolverSolveTime']))
            print("{: >20} {: >20}: {: >20}".format("SavileRow Total time:", param_filename, info_dict['SavileRowTotalTime']))

def test_random_instances_one():

    param_directory = "randomInstances"

    param_filename = "radom0.param"
    print(param_filename+":")
    read_sol_board  = get_sol_board_from_sol_files(param_directory, param_filename)
    # random_sol_board = get_random_sol_board()
    # print('{} {}'.format("rows:", len(random_sol_board)))
    # print('{} {}'.format("cols:", len(random_sol_board[0])))
    print_matrix(read_sol_board)
    # create_instance_file(random_sol_board, param_directory, param_filename)
    print('\n')#change later
    execute_savileRow_random(param_filename)
    print('\n')#change later

    info_dict = get_info(param_directory,param_filename+".info")
    print("{: >20} {: >20}: {: >20}".format("Num of Solutions: ", param_filename, info_dict['SolverSolutionsFound']))
    print("{: >20} {: >20}: {: >20}".format("Num of Nodes:", param_filename, info_dict['SolverNodes']))
    print("{: >20} {: >20}: {: >20}".format("Solver Total Time:", param_filename, info_dict['SolverTotalTime']))
    print("{: >20} {: >20}: {: >20}".format("Solve Time:", param_filename, info_dict['SolverSolveTime']))
    print("{: >20} {: >20}: {: >20}".format("SavileRow Total time:", param_filename, info_dict['SavileRowTotalTime']))

    if info_dict['SolverSolutionsFound'] > 1:
        print("HHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHH")
        diff_matrix = compare_two_solutions(param_directory, param_filename, random_sol_board)
        print("DIFF:")
        print_matrix(diff_matrix)
        counterr = compare_with_original(random_sol_board, diff_matrix)
        print('{}: {}'.format("COUNT in TEST", counterr))
        update_pfilename = param_filename
        updated_sol_board = update_instances(random_sol_board, diff_matrix, param_directory, update_pfilename)
        print("{: >20} {: >20}: {: >20}".format("Num of Solutions: ", param_filename, info_dict['SolverSolutionsFound']))
        print("{: >20} {: >20}: {: >20}".format("Num of Nodes:", param_filename, info_dict['SolverNodes']))
        print("{: >20} {: >20}: {: >20}".format("Solver Total Time:", param_filename, info_dict['SolverTotalTime']))
        print("{: >20} {: >20}: {: >20}".format("Solve Time:", param_filename, info_dict['SolverSolveTime']))
        print("{: >20} {: >20}: {: >20}".format("SavileRow Total time:", param_filename, info_dict['SavileRowTotalTime']))

def get_sol_board_from_sol_files(param_directory, param_filename):
    solution_filename = param_filename + ".solution."
    fpath = os.path.join(param_directory, param_filename)
    from_file = open(fpath, 'r')
    content = from_file.read()
    sol_board = re.search('(?<=(solution.{9}))\[\[.+?\)\]\n', content, re.S).group(0)

    # sol_board = re.search('(?<=(solution.{9}))\[\[.+?\)\]\n', content, re.S).group(0)
    sol_board_updated = re.sub(';int\(.+?\)','', sol_board)
    clean_sol_board = eval(sol_board_updated)
    return clean_sol_board

def test_special_cases(directory, board_filename):
    # fpath = os.path.join(directory, board_filename)
    # from_file = open(fpath, 'r')
    # content = from_file.read()
    # sol_board = re.search('\[\[.+?\)\]\n', content, re.S).group(0)
    clean_sol_board = rewriteboardFile(directory, board_filename)

    print(clean_sol_board)
    print(len(clean_sol_board[0]))
    print(len(clean_sol_board))


    # all_line_board = read_file_content(directory, board_filename)
    # print(convert_matrix_to_s(all_line_board))
    # print(all_line_board)

def main_test():
    # test_special_cases("permanent_examples", "random0board")
    #####################################################
    ###rewriting the content of the old board files######
    directory = "permanent_examples"
    filename = "random0board"

    mat = rewriteboardFile(directory, filename)
    print(len(mat))
    print(len(mat[0]))
    print(mat)
    content_of_file = convert_matrix_to_print_version(mat)
    write_param_file(directory, filename, content_of_file)

    print(content_of_file)
    #####################################################

def rewriteboardFile(directory, filename):
    fpath = os.path.join(directory, filename)
    from_file = open(fpath, 'r')
    content = from_file.read()
    print(content)
    print(type(content))
    print(content[0])
    print(content[1])
    print(content[2])
    # print(content[0])
    # x  = np.array(content)
    # y = x.astype(np.float)

    # sol_board = re.search('(?<=)\[\[.+?\)\]\n', content1, re.S).group(0)

    # evaluated=convert_matrix_to_print_version(content)
    # evaluated = eval(content)
    matrix_inner = []
    matrix_outer = []
    print(content[5])
    for c in content:
        print(c)
        if c == '0':
            print("cond1:")
            matrix_inner.append(0)
        elif c == '1':
            print("cond2:")
            matrix_inner.append(1)
        elif c == '\n':
            print("happening")
            print(matrix_inner)
            matrix_outer.append(matrix_inner)
            print(matrix_outer)
            matrix_inner = []
            print('{}: {}'.format("matrix_inner", matrix_inner))
    return matrix_outer

if __name__ == "__main__":
    random.seed(2)
    # execute only if run as a scripts
    # test_image_processor()
    test_random_instances()
    # test_random_instances_one()
    # main_test()
