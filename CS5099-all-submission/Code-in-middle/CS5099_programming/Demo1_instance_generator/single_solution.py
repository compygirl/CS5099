import os
import re
import string

from copy import copy, deepcopy
from instance_generator import *
from game_analyser import *


def compare_two_solutions(param_directory, param_filename, solution_board):
    solition_file1 = param_filename + ".solution.000001"
    solition_file2 = param_filename + ".solution.000002"

    fpath1 = os.path.join(param_directory, solition_file1)
    fpath2 = os.path.join(param_directory, solition_file2)

    from_file1 = open(fpath1, 'r')
    from_file2 = open(fpath2, 'r')

    content1 = from_file1.read()
    content2 = from_file2.read()

    sol_board1 = re.search('(?<=(solution.{9}))\[\[.+?\)\]\n', content1, re.S).group(0)
    sol_board2 = re.search('(?<=(solution.{9}))\[\[.+?\)\]\n', content2, re.S).group(0)

    sol_board_updated1 = re.sub(';int\(.+?\)', '', sol_board1)
    sol_board_updated2 = re.sub(';int\(.+?\)', '', sol_board2)

    clean_sol_board1 = eval(sol_board_updated1)
    clean_sol_board2 = eval(sol_board_updated2)

    # start comparing two solutions:
    difference_m = [[0 for x in range(len(clean_sol_board1[0]))] for y in range(len(clean_sol_board1))]

    for i in range(0, len(clean_sol_board1)):
        for j in range(0, len(clean_sol_board1[0])):
            if clean_sol_board1[i][j] != clean_sol_board2[i][j]:
                difference_m[i][j] = 1

    for i in range(0, len(difference_m)):
        for j in range(0, len(difference_m[0])):
            if difference_m[i][j] == 1 and solution_board[i][j] == 1:
                difference_m[i][j] = 0
                # break
    return difference_m


###########################################################
# compares the matrix with differences with the original solution_board
# returns the number of differences
###########################################################
def compare_with_original(orig_sol_board, difference_m):
    counter = 0
    for i in range(0, len(difference_m)):
        for j in range(0, len(difference_m[0])):
            if difference_m[i][j] == 1 and orig_sol_board[i][j] == 0:
                counter += 1
    return counter


def update_instances(orig_sol_board, difference_m, param_directory, image_filename):
    eprime_filename = "picross_solver.eprime"
    num_of_diff = compare_with_original(orig_sol_board, difference_m)
    updated_sol_board = [[0 for x in range(len(orig_sol_board))] for y in range(len(orig_sol_board[0]))]

    count = 0
    while count < num_of_diff:
        updated_sol_board = deepcopy(orig_sol_board)
        for i in range(0, len(orig_sol_board)):
            for j in range(0, len(orig_sol_board[0])):
                if difference_m[i][j] == 1 and orig_sol_board[i][j] == 0:
                    difference_m[i][j] = 0
                    updated_sol_board[i][j] = 1
                    break
            else:
                continue
            break

        print "UPDATED SOLUTION BOARD:"

        print_matrix(updated_sol_board)
        param_filename = "updated_" + image_filename.translate(None, string.punctuation) + ".param"
        create_instance_file(updated_sol_board, param_directory, param_filename)
        execute_savileRow(eprime_filename, param_directory, param_filename)
        # info_dict = get_info(param_directory,param_filename+".info")
        print_info(param_directory, param_filename)

        if info_dict['SolverSolutionsFound'] == 1:
            break
        else:
            count += 1

    return updated_sol_board
