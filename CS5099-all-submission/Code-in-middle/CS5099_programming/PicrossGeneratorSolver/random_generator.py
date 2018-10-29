# ####################################################################################
# ## This modules generates random solution board, which can have only binary values.
# ####################################################################################

from random import randint


def get_random_sol_board():
    main_rows = randint(1, 25)
    main_cols = randint(1, 25)

    solution_board = [[0 for x in range(main_cols)] for y in range(main_rows)]

    for i in range(0, main_rows):
        for j in range(0, main_cols):
            solution_board[i][j] = randint(0, 1)

    return solution_board
