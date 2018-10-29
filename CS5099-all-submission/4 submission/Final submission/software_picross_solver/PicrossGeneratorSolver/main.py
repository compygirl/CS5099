# -*- coding: utf-8 -*-
# +---------------------------------------------------------------------------+
# |Python 2.7.13                                                              |
# |                                                                           |
# |Author:           ID:090017799                                             |
# |                                                                           |
# |Module Name:      tester                                                   |
# |Module Purpose:   test a program                                           |
# |Input:            images from the image directory                          |
# |Output:           instances for the sovile row                             |
# |                  which runs and produces the solutions                    |
# +---------------------------------------------------------------------------+
###############################################################################
###############################################################################
from runner import *

if __name__ == "__main__":
    const_seed_value = 2
    type_minion = 4

    menu = """Please choose the type of generating instances for Nanogram (insert number 1-7):
    1. image based with - compare of 2 solutions
    2. image based with - compare all solutions
    3. random based with - compare 2 solutions
    4. random based with - compare all solutions
    5. fixed solution board - compare 2 solutions
    6. fixed solution board - compare all solutions
    7. create and display instances to print on paper and play with it
    8. for participants 
    9. Test Minion\n"""
    option = input(menu)
    # checks the instances based on the images,
    # but can improve the instances with several solutions in 2 ways:
    if option == 1 or option == 2:
        while True:
            ask_to_see_images = """What would you like to see (choose from 1 to 3:):
            1. Just Statistics information
            2. Just solution boards
            3. Both Statistics info & solution boards
            4. None of above - Just get results\n"""
            is_visualise = input(ask_to_see_images)
            if is_visualise > 4:
                print "You provided a wrong option. Please insert any number from 1 to 3!"
                continue
            else:
                id = uuid.uuid4()
                run_image_based_program(IMAGE_DIR, PARAM_DIR, option, id, is_visualise, type_minion)
                delete_unneded_files(PARAM_DIR)
                break
    # checks the instances based on the random generation,
    # but can improve the instances with several solutions in 2 ways:
    elif option == 3 or option == 4:
         while True:
            ask_to_see_images = """What would you like to see (choose from 1 to 3:):
            1. Just Statistics information
            2. Just solution boards
            3. Both Statistics info & solution boards
            4. None of above - Just get results\n"""
            ask_numer = """How many random instances you want run? (put any number > 1)"""
            is_visualise = input(ask_to_see_images)
            num_iterations = input(ask_numer)
            if is_visualise > 4:
                print "You provided a wrong option to see. Please insert any number from 1 to 3!"
                continue
            elif num_iterations < 1:
                print "You provided a wrong number of iterations. Please insert any number > 1!"
                continue
            else:
                random.seed(const_seed_value)
                id = uuid.uuid4()
                run_random_based_program(PARAM_DIR, EPRIME_FILE, option, id, is_visualise, num_iterations, type_minion)
                delete_unneded_files(PARAM_DIR)
                break
    # To have more experiments, this option allows the user to specify
    # the size of the board and number of cells by themselves and even the number of iterations 
    # to get different varieties with the same parameters:
    elif option == 5 or option == 6:
        while True:
            ask_to_see_images = """What would you like to see (choose from 1 to 3:):
            1. Just Statistics information
            2. Just solution boards
            3. Both Statistics info & solution boards
            4. None of above - Just get results\n"""
            ask_numer = """How many random instances you want run? (put any number > 1)"""
            prompt_rows = """Please insert Rows:\n"""
            prompt_cols = """Please insert Columns:\n"""
            promnt_coloured_cells = """Please insert number of coloured cells:\n"""
            rows = input(prompt_rows)
            columns = input(prompt_cols)
            num_coloured_cells = input(promnt_coloured_cells)
            is_visualise = input(ask_to_see_images)
            num_iterations = input(ask_numer)
            if num_coloured_cells >= (rows * columns):
                print "You have inserted invalid values! Please try again:"
                continue
            elif is_visualise > 4:
                print "You provided a wrong option to see. Please insert any number from 1 to 3!"
                continue
            elif num_iterations < 1:
                print "You provided a wrong number of iterations. Please insert any number > 1!"
                continue
            else:
                id = uuid.uuid4()
                run_fixed_boards(PARAM_DIR, EPRIME_FILE, id, option, rows, columns, num_coloured_cells, num_iterations, is_visualise, type_minion)
                delete_unneded_files(PARAM_DIR)
                break
    # This option allows to specify the parameters and display them, so that it could be print and played on a physical paper:
    elif option == 7:
        while True:
            ask_to_see_images = """What would you like to see (choose from 1 to 3:):
            1. Just Statistics information
            2. Just solution boards
            3. Both Statistics info & solution boards
            4. None of above - Just get results\n"""
            ask_numer = """How many random instances you want run? (put any number > 1)"""
            prompt_rows = """Please insert Rows:\n"""
            prompt_cols = """Please insert Columns:\n"""
            promnt_coloured_cells = """Please insert number of coloured cells:\n"""
            rows = input(prompt_rows)
            columns = input(prompt_cols)
            num_coloured_cells = input(promnt_coloured_cells)
            fixed_sol_board = get_fixed_sol_board(rows, columns, num_coloured_cells)

            is_visualise = input(ask_to_see_images)
            num_iterations = input(ask_numer)
            if num_coloured_cells >= (rows * columns):
                print "You have inserted invalid values! Please try again:"
                continue
            elif is_visualise > 4:
                print "You provided a wrong option to see. Please insert any number from 1 to 3!"
                continue
            elif num_iterations < 1:
                print "You provided a wrong number of iterations. Please insert any number > 1!"
                continue
            else:
                id = uuid.uuid4()
                list_sol_instances = run_fixed_boards(PARAM_DIR, EPRIME_FILE, id, option, rows, columns, num_coloured_cells, num_iterations, is_visualise, type_minion)
                delete_unneded_files(PARAM_DIR)
                number = len(list_sol_instances)
                while True:
                    print("-" * 65)
                    if number == 0:
                        break
                    ask_amount = """There are """ +str(number)+""" files with one solutions. How many of instance you want to print? """
                    instances_amount = input(ask_amount)
                    if instances_amount > number:
                        print "You exceeded the limit number!"
                        continue
                    elif instances_amount <= 0:
                        print "You provided negative invalid number! (Try >0)"
                        continue
                    else:
                        for i in range(0, instances_amount):
                            display_instance(list_sol_instances[i], "Instance #"+str(i+1))
                    break
                break
    # This option allows to specify the size and proportion of coloured cells and, but tries
    # to choose different levels among the boards with same parameters.
    elif option == 8:
        while True:
            ask_numer = """How many random instances you want run? (put any number > 1)"""
            prompt_rows = """Please insert Rows:\n"""
            prompt_cols = """Please insert Columns:\n"""
            promnt_coloured_cells = """Please proportion of coloured cells out of 100%:\n"""
            rows = input(prompt_rows)
            columns = input(prompt_cols)
            propotion = input(promnt_coloured_cells)
            num_coloured_cells = count_num_coloured_cells(propotion, rows, columns)
            num_iterations = input(ask_numer)

            if num_coloured_cells >= (rows * columns):
                print "You have inserted invalid values! Please try again:"
                continue
            else:
                random.seed(1)
                id = uuid.uuid4()
                list_of_boards, list_clues = get_for_participants(PARAM_DIR, EPRIME_FILE, id, rows, columns, num_coloured_cells, num_iterations, type_minion)
                print list_clues
                max_clues, many_clue_index = find_many_clues(list_clues)
                min_clues, small_amount_clues_index = find_less_clues(list_clues)
                if many_clue_index == small_amount_clues_index:
                    print "In this case the average of all clues is the same!"
                    display_instance(list_of_boards[many_clue_index], "All # clues are probably the same!")
                else:
                    easy_title = "EASY (size: " + str(rows) + " " + str(columns) + " coloured: " + str(propotion) + "%)"
                    hard_title = "HARD (size: " + str(rows) + " " + str(columns) + " coloured: " + str(propotion) + "%)"

                    display_instance(list_of_boards[small_amount_clues_index], easy_title)
                    display_instance(list_of_boards[many_clue_index], hard_title)
                break
    # This part allows to compare minion's options performance.
    elif option == 9:
        while True:
            ask_about_minion = """What type of Minion you would like to run:
            1. GAC,
            2. SAC,
            3. SSAC,
            4. SACBounds,
            5. SSACBounds,\n"""
            ask_to_see_images = """What would you like to see (choose from 1 to 3:):
            1. Just Statistics information
            2. Just solution boards
            3. Both Statistics info & solution boards
            4. None of above - Just get results\n"""
            ask_numer = """How many random instances you want run? (put any number > 1)"""
            type_minion = input(ask_about_minion)
            is_visualise = input(ask_to_see_images)
            num_iterations = input(ask_numer)
            if is_visualise > 4:
                print "You provided a wrong option to see. Please insert any number from 1 to 3!"
                continue
            elif num_iterations < 1:
                print "You provided a wrong number of iterations. Please insert any number > 1!"
                continue
            else:
                random.seed(const_seed_value)
                id = uuid.uuid4()
                run_random_based_program(PARAM_DIR, EPRIME_FILE, option, id, is_visualise, num_iterations, type_minion)
                delete_unneded_files(PARAM_DIR)
                break