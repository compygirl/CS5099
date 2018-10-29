from random import randint

def print_matrix(matrix):
    line=""
    for i in range(0, len(matrix)):
        for j in range(0, len(matrix[0])):
            line = line + " "+ str(matrix[i][j])
            line.lstrip()
        print(line)
        line = ""
    print('\n')

def convert_matrix_to_s(matrix):
    content = ""
    for row in matrix:
        content += str(row) + ",\n"
    return content

def convert_matrix_to_print_version(matrix):
    line="["
    for i in range(0, len(matrix)):
        line += "["
        for j in range(0, len(matrix[0])):
            line = line + str(matrix[i][j]) + " "
            # line.lstrip()
        # print(line)
        if i == len(matrix)-1:
            line = line + "]"
        else:
            line = line + "]\n"
    # print('\n')
    line = line +"]\n"

    return line

    # content = ""
    # for row in matrix:
    #     content += str(row) + ",\n"
    # return content


def generate_random():
    return randint(0, 9)
