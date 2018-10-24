# ####################################################################################
# ## This module file deals with all matrices needed in the game:
# ##
# ##  1. prints 2D matrices
# ##
# ##  2. converts matrix to string.
# ##
# ##  3. converts the matrix
# ####################################################################################

from random import randint


def print_matrix(matrix):
    line = ""
    enum_line = " " * 5
    for i in range(0, len(matrix)):
        line += "{:>3}".format(str(i + 1)) + "  "
        for j in range(0, len(matrix[0])):
            enum_line += "{:>2}".format(str(j + 1)) + " "
            line += "{:>2}".format(str(matrix[i][j])) + " "
        if i == 0:
            print(enum_line)
        print(line)
        line = ""
    print('\n')


def convert_matrix_to_s(matrix):
    content = ""
    for row in matrix:
        content += str(row) + ",\n"
    return content


def convert_matrix_to_print_version(matrix):
    line = "["
    for i in range(0, len(matrix)):
        line += "["
        for j in range(0, len(matrix[0])):
            line = line + str(matrix[i][j]) + " "
        if i == len(matrix) - 1:
            line = line + "]"
        else:
            line = line + "]\n"
    line = line + "]\n"
    return line
