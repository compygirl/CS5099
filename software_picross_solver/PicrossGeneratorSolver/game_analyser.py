# ####################################################################################
# ## This module file get the information of the instances for later analysis:
# ## 1. execute_savileRow: triggers the savilerow command using obtained
# ##    instances from the system
# ##
# ## 2. get_info_from_file: gets the needed time and nodes info from the .info
# ##    file of the executed instance and stores in dictionary called info_dict
# ##
# ## 3. print_info_from_file: prints SavileRow info from dictionary
# ## 4. get_stat_about_solution_board: gets the formation about the solution board.
# ##    Like size, proportion of coloured cells and adds the UUID, which is a unique
# ##    ID for each execution. Some other quantitative data about the main grid: sol.board.
# ## 
# ## 5. get_stat_of_random_generation: counts the general statistics of how many
# ##    instances generated randomly can give one solution, many solutions or no
# ##    solutions, and among those which were enhanced how many.
# ####################################################################################

import os
from subprocess import call
from file_manager import *
from single_solution import *
from collections import OrderedDict
info_dict = OrderedDict()
stat_about_solutions = {}
stat_about_solutions['NoSolutions'] = 0
stat_about_solutions['OneSolution'] = 0
stat_about_solutions['MoreSolutions'] = 0
stat_about_solutions['GotIntoOneSolution'] = 0
stat_percentage = OrderedDict()
stat_percentage['NoSolutions'] = 0
stat_percentage['OneSolution'] = 0
stat_percentage['MoreSolutions'] = 0
stat_percentage['GotIntoOneSolution'] = 0


def execute_savileRow(eprime_filename, param_directory, param_filename, type_minion):
    print("-" * 65)
    print'{:^65}'.format("SAVILE ROW of " + param_filename)
    print("-" * 65)

    if type_minion == 1:
        print "GAC Minion"

        call(["zsh", "-c", "savilerow " "" + eprime_filename +
        " " + param_directory + "/" + param_filename +
        " -run-solver -num-solutions 10 -solver-options" +
        " '-cpulimit 10 -varorder conflict -prop-node GAC'"])
    elif type_minion == 2:
        print "SAC Minion"

        call(["zsh", "-c", "savilerow " "" + eprime_filename +
        " " + param_directory + "/" + param_filename +
        " -run-solver -num-solutions 10 -solver-options" +
        " '-cpulimit 10 -varorder conflict -prop-node SAC'"])      
    elif type_minion == 3:
        print "SSAC Minion"

        call(["zsh", "-c", "savilerow " "" + eprime_filename +
        " " + param_directory + "/" + param_filename +
        " -run-solver -num-solutions 10 -solver-options" +
        " '-cpulimit 10 -varorder conflict -prop-node SSAC'"])
    elif type_minion == 4:
        print "SACBounds Minion"

        call(["zsh", "-c", "savilerow " "" + eprime_filename +
        " " + param_directory + "/" + param_filename +
        " -run-solver -num-solutions 10 -solver-options" +
        " '-cpulimit 10 -varorder conflict -prop-node SACBounds'"])      
    elif type_minion == 5:
        print "SSACBounds Minion"

        call(["zsh", "-c", "savilerow " "" + eprime_filename +
        " " + param_directory + "/" + param_filename +
        " -run-solver -num-solutions 10 -solver-options" +
        " '-cpulimit 10 -varorder conflict -prop-node SSACBounds'"])


    # call(["zsh", "-c", "savilerow " "" +
    #     eprime_filename + " " + param_directory + "/" + param_filename +
    #     " -run-solver -num-solutions 10 -solver-options" +
    #     " '-varorder conflict -prop-node SACBounds'"])


def get_info_from_file(directory, param_filename, type_minion):
    all_lines = read_file_content(directory, param_filename + ".info")
    if type_minion == 1:
        info_dict['Minion'] = 'GAC'
    elif type_minion == 2:
        info_dict['Minion'] = 'SAC'
    elif type_minion == 3:
        info_dict['Minion'] = 'SSAC'
    elif type_minion == 4:
        info_dict['Minion'] = 'SACBounds'
    elif type_minion == 5:
        info_dict['Minion'] = 'SSACBounds'
    # info_dict['Minion']
    for line in all_lines:
        if line.startswith("SolverSolutionsFound"):
            split_line_arr = line.split(':')
            info_dict['SolverSolutionsFound'] = int(split_line_arr[1])
        elif line.startswith("SolverNodes"):
            split_line_arr = line.split(':')
            info_dict['SolverNodes'] = int(split_line_arr[1])
        elif line.startswith("SolverTotalTime"):
            split_line_arr = line.split(':')
            info_dict['SolverTotalTime'] = float(split_line_arr[1])
        elif line.startswith("SolverSolveTime"):  #
            split_line_arr = line.split(':')
            info_dict['SolverSolveTime'] = float(split_line_arr[1])
        elif line.startswith("SavileRowTotalTime"):
            split_line_arr = line.split(':')
            info_dict['SavileRowTotalTime'] = float(split_line_arr[1])
    return info_dict


def print_info_from_file(directory, param_filename):
    print("-" * 65)
    print'{:^65}'.format("Stat info of: " + param_filename)
    print("-" * 65)
    print("|{: <30}|{: >20}| {: <20}|".format("Num of Solutions: ", param_filename, info_dict['SolverSolutionsFound']))
    print("|{: <30}|{: >20}| {: <20}|".format("Num of Nodes:", param_filename, info_dict['SolverNodes']))
    print("|{: <30}|{: >20}| {: <20}|".format("Solver Total Time:", param_filename, info_dict['SolverTotalTime']))
    print("|{: <30}|{: >20}| {: <20}|".format("Solve Time:", param_filename, info_dict['SolverSolveTime']))
    print("|{: <30}|{: >20}| {: <20}|".format("SavileRow Total time:", param_filename, info_dict['SavileRowTotalTime']))

    print("|{: <30}|{: >20}| {: <20}|".format("Num of Rows: ", param_filename, info_dict['Rows']))
    print("|{: <30}|{: >20}| {: <20}|".format("Num of Columns: ", param_filename, info_dict['Columns']))
    print("|{: <30}|{: >20}| {: <20}|".format("Num of Total Grids: ", param_filename, info_dict['TotalGrids']))
    print("|{: <30}|{: >20}| {: <20}|".format("Num of Coloured cells: ", param_filename, info_dict['NumColoured']))
    print("|{: <30}|{: >20}| {: <20}|".format("ProportionColoured: ", param_filename, info_dict['ProportionColoured']))
    print("|{: <30}|{: >20}| {: <20}|".format("UUID: ", param_filename, info_dict['UUID']))
    print("|{: <30}|{: >20}| {: <20}|".format("#Step: ", param_filename, info_dict['STEPS']))


def get_stat_about_solution_board(solution_board, id, steps):
    count_coloured = 0
    info_dict['Rows'] = len(solution_board)
    info_dict['Columns'] = len(solution_board[0])
    total_grids = len(solution_board) * len(solution_board[0])
    info_dict['TotalGrids'] = len(solution_board) * len(solution_board[0])
    for i in range(0, len(solution_board)):
        for j in range(0, len(solution_board[0])):
            if solution_board[i][j] == 1:
                count_coloured += 1
    info_dict['NumColoured'] = count_coloured
    info_dict['ProportionColoured'] = round(float(count_coloured) / float(total_grids), 2)
    info_dict['UUID'] = id
    info_dict['STEPS'] = steps
    return info_dict


def put_into_dictionary(num_of_solutions):
    # print "initial num_of_solutions: " + str(num_of_solutions)
    if num_of_solutions == 0:
        stat_about_solutions['NoSolutions'] += 1
    elif num_of_solutions == 1:
        stat_about_solutions['OneSolution'] += 1
    elif num_of_solutions > 1:
        stat_about_solutions['MoreSolutions'] += 1
    return stat_about_solutions


def get_stat_of_random_generation(total_amount_of_instances):
    stat_percentage['NoSolutions'] = float(stat_about_solutions['NoSolutions']) / total_amount_of_instances
    stat_percentage['OneSolution'] = float(stat_about_solutions['OneSolution']) / total_amount_of_instances
    stat_percentage['MoreSolutions'] = float(stat_about_solutions['MoreSolutions']) / total_amount_of_instances
    if(stat_about_solutions['MoreSolutions']):
        denum = float(stat_about_solutions['MoreSolutions'])
        stat_percentage['GotIntoOneSolution'] = float(stat_about_solutions['GotIntoOneSolution']) / denum
    return stat_percentage


def get_general_info(steps, info_dict, param_filename):
    if steps == 0 and info_dict['SolverSolutionsFound'] == 1:
        print("!" * 80)
        print("!" * 4 + '{:^72}'.format(param_filename + ' IS SOLVABLE') + "!" * 4)
        print("!" * 80)
    elif steps > 0 and info_dict['SolverSolutionsFound'] == 1:
        print("!" * 80)
        print("!" * 4 + '{:^72}'.format(param_filename + ' IS SOLVABLE after ' + str(steps) + ' STEPS') + "!" * 4)
        print("!" * 80)
        stat_about_solutions['GotIntoOneSolution'] += 1
    elif steps == 0 and info_dict['SolverSolutionsFound'] > 1:
        print("!" * 80)
        print("!" * 4 + '{:^72}'.format(param_filename + ' NEVER HAPPENS? (only if never iterated to be improved) ') + "!" * 4)
        print("!" * 80)
    elif steps > 0 and info_dict['SolverSolutionsFound'] > 1:  # >10 solutions
        print("!" * 80)
        print("!" * 4 + '{:^72}'.format(param_filename + ' HAS TOO MANY SOLUTIONS ') + "!" * 4)
        print("!" * 80)
    elif steps == 0 and info_dict['SolverSolutionsFound'] == 0:
        print("!" * 80)
        print("!" * 4 + '{:^72}'.format(param_filename + ' HAS NO SOLUTIONS ') + "!" * 4)
        print("!" * 80)


def print_general_info(totl_num_files):
    # Print general statistics:
    print("-" * 65)
    print'{:^65}'.format("Stat info of all files")
    print("-" * 65)
    print("|{: <56}|{: <20}|".format("Total amount of files: ", totl_num_files))
    print("|{: <56}|{: <20}|".format("# of files with no solution: ", stat_about_solutions['NoSolutions']))
    print("|{: <56}|{: <20}|".format("# of files with one solution: ", stat_about_solutions['OneSolution']))
    print("|{: <56}|{: <20}|".format("# of files with many solution: ", stat_about_solutions['MoreSolutions']))
    print("|{: <56}|{: <20}|".format("# of files that got in one sol-n/files with many sol-s: ", stat_about_solutions['GotIntoOneSolution']))

    get_stat_of_random_generation(totl_num_files)
    print("|{: <56}|{: <20}|".format("% of files with no solution: ", stat_percentage['NoSolutions']))
    print("|{: <56}|{: <20}|".format("% of files with one solution: ", stat_percentage['OneSolution']))
    print("|{: <56}|{: <20}|".format("% of files with many solution: ", stat_percentage['MoreSolutions']))
    print("|{: <56}|{: <20}|".format("% of files that got in one sol-n/files with many sol-s: ", stat_percentage['GotIntoOneSolution']))
