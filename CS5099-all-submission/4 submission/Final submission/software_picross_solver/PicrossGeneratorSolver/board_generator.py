# #########################################################################
# ## This modules generates random solution board and solution board with
# specified size and number of ones.
# ##########################################################################

from random import randint


def get_random_sol_board():
    # -----------------------------------------------------
    # This method generates random solution board
    # with random size and random amount of ones inside:
    # -----------------------------------------------------
    main_rows = randint(1, 25)
    main_cols = randint(1, 25)

    solution_board = [[0 for x in range(main_cols)] for y in range(main_rows)]

    for i in range(0, main_rows):
        for j in range(0, main_cols):
            solution_board[i][j] = randint(0, 1)
    return solution_board


def get_fixed_sol_board(rows, columns, coloured_cells):
    # -----------------------------------------------------
    # This function generates solution board with the specified size and
    # amount of the coloured cells on the solution board
    # -----------------------------------------------------
    counter = 0
    solution_board = [[0 for x in range(columns)] for y in range(rows)]
    while True:
        index_row = randint(0, rows - 1)
        index_col = randint(0, columns - 1)
        if solution_board[index_row][index_col] == 1:
            continue
        elif counter == coloured_cells:
            break
        else:
            solution_board[index_row][index_col] = 1
            counter += 1
    return solution_board
