# -*- coding: utf-8 -*-
# +-----------------------------------------------------------------------------+
# |Python 2.7.13                                                                |
# |                                                                             |
# |Author:           ID:090017799                                               |
# |                                                                             |
# |Module Name:      tester                                                     |
# |Module Purpose:   test a program                                             |
# |Input:            images from the image directory                            |
# |Output:           instances for the sovile row                               |
# |                  which runs and produces the solutions                      |
# +-----------------------------------------------------------------------------+
####################################################################################
####################################################################################
from image_processor import *
from matrix_processor import *
from instance_generator import *
from game_analyser import *
from file_manager import *
from single_solution import *
from board_generator import *
from constants import *
from solution_processor import *
from level_gen_helper import *


# import solution_processor

import random
import string
import os
import re
import sys

import numpy as np
import uuid


def run_image_based_program(image_directory, param_directory, main_menu_option, id, is_visualise, type_minion):
    num_files = 0
    for image_filename in os.listdir(image_directory):  # iterate through images
        steps = 0  # this counts how many steps made to enhance instance to get one solution
        if image_filename.endswith(".jpg") or image_filename.endswith(".png"):
            param_filename = imagename_to_paramname(image_filename)
            num_files += 1

            print("#" * 80)
            print("#" * 4 + '{:^72}'.format('SOLUTION BOARD of ' + image_filename) + "#" * 4)
            print("#" * 80)

            img = read_image(image_directory, image_filename)
            solution_board = create_solution_board(img)
            counter_diff = 0
            num_diff = 0
            diff_matrix = [[0 for x in range(len(solution_board[0]))] for y in range(len(solution_board))]
            while True:
                create_instance_file(solution_board, param_directory, param_filename)
                execute_savileRow(EPRIME_FILE, param_directory, param_filename, type_minion)
                get_info_from_file(param_directory, param_filename, type_minion)# reads .info file # reads .info file
                get_stat_about_solution_board(solution_board, id, steps) # puts to info_dict about size ...
                if is_visualise == 1:
                    print_info_from_file(param_directory, param_filename) # prints all stat about each solution from info_dict
                elif is_visualise == 2:
                    print_matrix(solution_board) # prints binary representation of solution board
                    visualise_board(solution_board, diff_matrix)   # prints the solution board to the consode in more readable way than binary
                elif is_visualise == 3:
                    print_info_from_file(param_directory, param_filename) # prints all stat about each solution from info_dict
                    print_matrix(solution_board) # prints binary representation of solution board
                    visualise_board(solution_board, diff_matrix)  # prints the solution board to the consode in more readable way than binary
                num_sols = get_num_solutions(param_directory, param_filename, solution_board) # will be returning initial num. of solutions
                if steps == 0:
                    put_into_dictionary(num_sols)  # to the dictionary of general stat of all files

                #writing to the csv file needed information:
                stat_directory = STAT_DIR
                csvfile = INFO_CSV_FILE
                if not check_file_exist(stat_directory, csvfile):
                    create_csv_file_dict(stat_directory, csvfile, info_dict)
                add_values_to_csv(stat_directory, csvfile, info_dict, param_filename)

                if num_diff != 0 and counter_diff == num_diff:
                    break
                if info_dict['SolverSolutionsFound'] > 1:
                    steps += 1
                    counter_diff += 1
                    if counter_diff == 1:
                        if main_menu_option == 1:
                            diff_matrix, num_diff = compare_two_solutions(param_directory, param_filename,
                                solution_board)
                        elif main_menu_option == 2:
                            diff_matrix, num_all_diff = compare_all_solutions(param_directory, param_filename,
                                solution_board)
                    print("-" * 65)
                    print'{:^65}'.format("!!!!!    MORE SOLUTIONS (>1): " + param_filename + "    !!!!!")
                    print("-" * 65)

                    solution_board = update_sol_board(solution_board, diff_matrix)
                    continue
                else:
                    break
            get_general_info(steps, info_dict, param_filename) # about all files
    print_general_info(num_files)# prints the stat about all files
    print ('{} {} {}'.format("Overall ", num_files, ".param files were created"))


def run_random_based_program(param_directory, eprime_solver, option, id, is_visualise, num_iterations, type_minion):
    for i in range(0, num_iterations):
        steps = 0
        param_filename = "random" + str(i) + ".param"

        print("#" * 80)
        print("#" * 4 + '{:^72}'.format('SOLUTION BOARD for ' + param_filename) + "#" * 4)
        print("#" * 80)

        random_sol_board = get_random_sol_board()
        num_diff = 0
        counter_diff = 0
        diff_matrix = [[0 for x in range(len(random_sol_board[0]))] for y in range(len(random_sol_board))]
        while True:
            create_instance_file(random_sol_board, param_directory, param_filename)
            execute_savileRow(EPRIME_FILE, param_directory, param_filename, type_minion)
            get_info_from_file(param_directory, param_filename, type_minion) # reads .info file
            get_stat_about_solution_board(random_sol_board, id, steps)  # puts to info_dict about size ...
            if is_visualise == 1:
                print_info_from_file(param_directory, param_filename) # prints all stat about each solution from info_dict
            elif is_visualise == 2:
                print_matrix(random_sol_board) # prints binary representation of solution board
                visualise_board(random_sol_board, diff_matrix)  # prints the solution board to the consode in more readable way than binary
            elif is_visualise == 3:
                print_info_from_file(param_directory, param_filename) # prints all stat about each solution from info_dict
                print_matrix(random_sol_board) # prints binary representation of solution board
                visualise_board(random_sol_board, diff_matrix)  # prints the solution board to the consode in more readable way than binary

            # this for general statistics to count how many files might have 1 sol-n, many sol-s, no sol-s.
            num_sols = get_num_solutions(param_directory, param_filename, random_sol_board) # will be returning initial num. of solutions
            if steps == 0:
                put_into_dictionary(num_sols)  # to the dictionary of general stat of all files

            #writing to the csv file needed information:
            stat_directory = STAT_DIR
            csvfile = INFO_CSV_FILE
            if not check_file_exist(stat_directory, csvfile):
                create_csv_file_dict(stat_directory, csvfile, info_dict)
            add_values_to_csv(stat_directory, csvfile, info_dict, param_filename)

            if num_diff != 0 and counter_diff == num_diff:
                break
            if info_dict['SolverSolutionsFound'] > 1:
                steps += 1
                counter_diff += 1
                if counter_diff == 1:
                    if option == 3:
                        diff_matrix, num_diff = compare_two_solutions(param_directory, param_filename, random_sol_board)
                    elif option == 4 or option == 9:
                        diff_matrix, num_all_diff = compare_all_solutions(param_directory, param_filename,
                            random_sol_board)
                    # elif option == 9:
                    #     diff_matrix, num_all_diff = compare_all_solutions(param_directory, param_filename,
                    #         random_sol_board)
                print("-" * 65)
                print'{:^65}'.format("!!!!!    MORE SOLUTIONS (>1): " + param_filename + "    !!!!!")
                print("-" * 65)

                random_sol_board = update_sol_board(random_sol_board, diff_matrix)
                continue
            else:
                break

        get_general_info(steps, info_dict, param_filename) # about all files
    print_general_info(num_iterations)# prints the stat about all files
    print ('{} {} {}'.format("Overall ", num_iterations, ".param files were created"))


def run_fixed_boards(param_directory, eprime_solver, id, option, rows, columns, num_coloured_cells, num_iterations, is_visualise, type_minion):
    list_instances_with_one_sol = []
    for i in range(0, num_iterations):
        steps = 0
        param_filename = "fixed" + str(i) + ".param"

        print("#" * 80)
        print("#" * 4 + '{:^72}'.format('SOLUTION BOARD for ' + param_filename) + "#" * 4)
        print("#" * 80)

        fixed_sol_board = get_fixed_sol_board(rows, columns, num_coloured_cells)

        num_diff = 0
        counter_diff = 0
        diff_matrix = [[0 for x in range(len(fixed_sol_board[0]))] for y in range(len(fixed_sol_board))]
        while True:
            create_instance_file(fixed_sol_board, param_directory, param_filename)
            execute_savileRow(EPRIME_FILE, param_directory, param_filename, type_minion)
            get_info_from_file(param_directory, param_filename, type_minion) # reads .info file
            get_stat_about_solution_board(fixed_sol_board, id, steps)  # puts to info_dict about size ...
            if is_visualise == 1:
                print_info_from_file(param_directory, param_filename) # prints all stat about each solution from info_dict
            elif is_visualise == 2:
                print_matrix(fixed_sol_board) # prints binary representation of solution board
                visualise_board(fixed_sol_board, diff_matrix)  # prints the solution board to the consode in more readable way than binary
            elif is_visualise == 3:
                print_info_from_file(param_directory, param_filename) # prints all stat about each solution from info_dict
                print_matrix(fixed_sol_board) # prints binary representation of solution board
                visualise_board(fixed_sol_board, diff_matrix)  # prints the solution board to the consode in more readable way than binary
            num_sols = get_num_solutions(param_directory, param_filename, fixed_sol_board) # will be returning initial num. of solutions
            if info_dict['SolverSolutionsFound'] == 1:
                list_instances_with_one_sol.append(fixed_sol_board)
            if steps == 0:
                put_into_dictionary(num_sols)  # to the dictionary of general stat of all files
            
            #writing to the csv file needed information:
            stat_directory = STAT_DIR
            csvfile = CONST_INFO_CSV_FILE
            if not check_file_exist(stat_directory, csvfile):
                create_csv_file_dict(stat_directory, csvfile, info_dict)
            add_values_to_csv(stat_directory, csvfile, info_dict, param_filename)

            if num_diff != 0 and counter_diff == num_diff:
                break
            if info_dict['SolverSolutionsFound'] > 1:
                steps += 1
                counter_diff += 1
                if counter_diff == 1:
                    if option == 5:
                        diff_matrix, num_diff = compare_two_solutions(param_directory, param_filename,
                                fixed_sol_board)
                    elif option == 6:
                        diff_matrix, num_all_diff = compare_all_solutions(param_directory, param_filename,
                                fixed_sol_board)
                    elif option == 7:
                        diff_matrix, num_diff = compare_all_solutions(param_directory, param_filename,
                                fixed_sol_board)
                print("-" * 65)
                print'{:^65}'.format("!!!!!    MORE SOLUTIONS (>1): " + param_filename + "    !!!!!")
                print("-" * 65)

                fixed_sol_board = update_sol_board(fixed_sol_board, diff_matrix)
                continue
            else:
                break
        get_general_info(steps, info_dict, param_filename) # about all files
    print_general_info(num_iterations)# prints the stat about all files

    print ('{} {} {}'.format("Overall ", num_iterations, ".param files were created"))
    return list_instances_with_one_sol


def get_for_participants(param_directory, eprime_solver, id, rows, columns, num_coloured_cells, num_iterations, type_minion):
    list_of_clues = [-1] * num_iterations
    list_of_all_boards = []
    for i in range(0, num_iterations):

        param_filename = "participants" + str(i) + ".param"

        print("#" * 80)
        print("#" * 4 + '{:^72}'.format('SOLUTION BOARD for ' + param_filename) + "#" * 4)
        print("#" * 80)
        fixed_sol_board = get_fixed_sol_board(rows, columns, num_coloured_cells)
        list_of_all_boards.append(fixed_sol_board)

        visualise_board2(fixed_sol_board)  # prints the solution board to the consode in more readable way than binary
        create_instance_file(fixed_sol_board, param_directory, param_filename)
        execute_savileRow(EPRIME_FILE, param_directory, param_filename, type_minion)
        get_info_from_file(param_directory, param_filename, type_minion)  # reads .info file
        get_stat_about_solution_board(fixed_sol_board, id, 0)  # puts to info_dict about size ...
        num_sols = get_num_solutions(param_directory, param_filename, fixed_sol_board) # will be returning initial num. of solutions
        put_into_dictionary(num_sols)  # to the dictionary of general stat of all files
        print_info_from_file(param_directory, param_filename) # prints all stat about each solution from info_dict
        stat_directory = STAT_DIR

        #writing to the csv file needed information:
        csvfile = PARTICIPANTS_CSV_FILE
        if not check_file_exist(stat_directory, csvfile):
            create_csv_file_dict(stat_directory, csvfile, info_dict)
        add_values_to_csv(stat_directory, csvfile, info_dict, param_filename)
        if info_dict['SolverSolutionsFound'] == 1:
            list_of_clues[i] = count_total_clues(fixed_sol_board)
        get_general_info(0, info_dict, param_filename) # about all files
    print_general_info(num_iterations)# prints the stat about all files
    print ('{} {} {}'.format("Overall ", num_iterations, ".param files were created"))
    return list_of_all_boards, list_of_clues
