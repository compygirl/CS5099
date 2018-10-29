# ####################################################################################################
# ## This modules deals with param files, which generated more than 1 solution:
# ##
# ## 1. compares two solutions and original solution board and returns matrix 
# ##    with differences;
# ##
# ## 2. updates the original solution board by adding the pixels one by one from the matrix with
# ##    differences;
# ##
# ## 2. after the places with differences in the matrix was obtained, the original
# ##    solution board could be changed so that we could try to find an instance with one solution.
# ####################################################################################################

import os
import re
import string

from copy import copy, deepcopy
from instance_generator import *
from game_analyser import *
from constants import EPRIME_FILE


def compare_two_solutions(param_directory, param_filename, orig_solution_board):
    solution_file1 = param_filename + ".solution.000001"
    solution_file2 = param_filename + ".solution.000002"

    fpath1 = os.path.join(param_directory, solution_file1)
    fpath2 = os.path.join(param_directory, solution_file2)

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
    num_diff = 0
    for i in range(0, len(clean_sol_board1)):
        for j in range(0, len(clean_sol_board1[0])):
            if clean_sol_board1[i][j] != clean_sol_board2[i][j] and orig_solution_board[i][j] == 0:
                difference_m[i][j] = 1
                num_diff += 1
    return difference_m, num_diff


def update_sol_board(updated_sol_board, difference_m):
    for i in range(0, len(updated_sol_board)):
        for j in range(0, len(updated_sol_board[0])):
            if difference_m[i][j] == 1 and updated_sol_board[i][j] == 0:
                # difference_m[i][j] = 0
                updated_sol_board[i][j] = 1
                break
        else:
            continue
        break
    return updated_sol_board


def get_num_solutions(param_directory, param_filename, orig_solution_board):
    num_sols = 0
    for sol_filename in os.listdir(param_directory):
        if sol_filename.startswith(param_filename + ".solution"):
            num_sols += 1
    return num_sols


def get_all_sol_boards(param_directory, param_filename, orig_solution_board):
    list_of_solutions = []
    for sol_filename in os.listdir(param_directory):
        if sol_filename.startswith(param_filename + ".solution"):
            fpath = os.path.join(param_directory, sol_filename)
            from_file = open(fpath, 'r')
            content = from_file.read()
            sol_board = re.search('(?<=(solution.{9}))\[\[.+?\)\]\n', content, re.S).group(0)
            sol_board_updated = re.sub(';int\(.+?\)', '', sol_board)
            clean_sol_board = eval(sol_board_updated)
            list_of_solutions.append(clean_sol_board)
    return list_of_solutions


def compare_all_solutions(param_directory, param_filename, orig_solution_board):
    num_all_diff = 0
    difference_m = [[0 for x in range(len(orig_solution_board[0]))] for y in range(len(orig_solution_board))]
    list_of_solutions = get_all_sol_boards(param_directory, param_filename, orig_solution_board)
    for i in range(0, len(list_of_solutions[0])):
        for j in range(0, len(list_of_solutions[0][0])):
            for k in range(0, (len(list_of_solutions) - 1)):
                if list_of_solutions[k][i][j] != list_of_solutions[k + 1][i][j] and orig_solution_board[i][j] == 0:
                    difference_m[i][j] = 1
                    num_all_diff += 1
    return difference_m, num_all_diff
