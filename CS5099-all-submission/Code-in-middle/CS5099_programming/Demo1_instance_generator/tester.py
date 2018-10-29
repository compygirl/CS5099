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
from constants import *

import os  # lists the images

# ##############################################################################
# ## 1. gets the needed info from the .info file and
# ##    stores in dictionary called info_dict
# ## 2. executes the savile row comand on the solver and the obtained param files.
# ##############################################################################


def test_image_processor():
    iterate_through_images(IMAGE_DIR, PARAM_DIR)
    delete_unneded_files(PARAM_DIR)


def iterate_through_images(image_directory, param_directory):
    counter = 0
    eprime_filename = "picross_solver.eprime"
    for image_filename in os.listdir(image_directory):
        if image_filename.endswith(".jpg") or image_filename.endswith(".png"):
            print("=" * 80)
            img = read_image(image_directory, image_filename)
            solution_board = create_solution_board(img)
            print("SOLUTION BOARD of " + image_filename)
            print_matrix(solution_board)
            param_filename = transfer_imagename_to_param(image_filename)
            create_instance_file(solution_board, param_directory, param_filename)
            execute_savileRow(eprime_filename, param_directory, param_filename)
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

if __name__ == "__main__":
    test_image_processor()
