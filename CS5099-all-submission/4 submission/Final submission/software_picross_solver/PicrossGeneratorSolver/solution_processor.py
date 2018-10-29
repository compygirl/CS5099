# -*- coding: utf-8 -*-
# ####################################################################################
# ## This modules deals with the solution files that resulted from the savilerow:
# ##
# ## 1. visualise_board: prints the solution board to visualise it in more deatails and
# ##    also it prints the cells which were changed in order to show the enhancement.
# ##
# ## 2. visualise_board2: prints thes solution borsd on the terminal but without
# ##    any changes made to the board, it also takes only one parameter just for printing out
# ##    the board without much analysis need. 
# ####################################################################################


def visualise_board(solution_board, diff_matrix):
    enum_line = " " * 5
    line = ""
    for i in range(0, len(solution_board)):
        line += "{:>2}".format(str(i + 1)) + "  "
        for j in range(0, len(solution_board[0])):
            enum_line += "{:>2}".format(str(j + 1)) + " "
            line += '│' if (j % 5 == 0) else ('_' if ((i + 1) % 5 == 0) else ' ')
            line += '⏹' if(solution_board[i][j] == 1 and diff_matrix[i][j] == 0) else ('⛎'
                if (solution_board[i][j] == 1 and diff_matrix[i][j] == 1) else ('_' if((i + 1) % 5 == 0) else ' '))
            line += '_' if ((i + 1) % 5 == 0) else ' '
        if i == 0:
            print(enum_line)
        print (line)
        line = ""
    print('\n')


def visualise_board2(solution_board):
    enum_line = " " * 5
    line = ""
    for i in range(0, len(solution_board)):
        line += "{:>2}".format(str(i + 1)) + "  "
        for j in range(0, len(solution_board[0])):
            enum_line += "{:>2}".format(str(j + 1)) + " "
            line += '│' if (j % 5 == 0) else ('_' if ((i + 1) % 5 == 0) else ' ')
            line += '⏹' if(solution_board[i][j] == 1) else ('_' if ((i + 1) % 5 == 0) else ' ')
            line += '_' if ((i + 1) % 5 == 0) else ' '
        if i == 0:
            print(enum_line)
        print (line)
        line = ""
    print('\n')
