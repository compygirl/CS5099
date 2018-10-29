import os
from subprocess import call
from file_manager import *

# ##############################################################################
# ## 1. gets the needed info from the .info file and
# ##    stores in dictionary called info_dict
# ## 2. executes the savile row comand on the solver and the obtained param files.
# ##############################################################################

info_dict = {}


def execute_savileRow(eprime_filename, param_directory, param_filename):
    print("=" * 80)
    print("SAVILE ROW of " + param_filename + ":\n")
    call(["zsh", "-c", "savilerow " "" + eprime_filename + " " + param_directory + "/" + param_filename +
        " -run-solver -num-solutions 5 -solver-options '-varorder conflict -prop-node SACBounds'"])


def get_info(directory, param_filename):
    all_lines = read_file_content(directory, param_filename)

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
        elif line.startswith("SolverSolveTime"):  #not really needed
            split_line_arr = line.split(':')
            info_dict['SolverSolveTime'] = float(split_line_arr[1])
        elif line.startswith("SavileRowTotalTime"):
            split_line_arr = line.split(':')
            info_dict['SavileRowTotalTime'] = float(split_line_arr[1])
    return info_dict


def print_info(directory, param_filename):
    print("=" * 80)
    print(".INFO file:\n")
    info_dict = get_info(directory, param_filename + ".info")
    print("{: >20} {: >20}: {: >20}".format("Num of Solutions: ", param_filename, info_dict['SolverSolutionsFound']))
    print("{: >20} {: >20}: {: >20}".format("Num of Nodes:", param_filename, info_dict['SolverNodes']))
    print("{: >20} {: >20}: {: >20}".format("Solver Total Time:", param_filename, info_dict['SolverTotalTime']))
    print("{: >20} {: >20}: {: >20}".format("Solve Time:", param_filename, info_dict['SolverSolveTime']))
    print("{: >20} {: >20}: {: >20}".format("SavileRow Total time:", param_filename, info_dict['SavileRowTotalTime']))
