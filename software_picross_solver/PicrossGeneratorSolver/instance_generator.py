# # #########################################################################
# ## This module file deals with content of the param files:
# ##  1. create_top_matrix and create_left_matrix: based on the solution
# ##     board create instance's top matrix and left matrix;
# ##
# ##  2. create_content_of_inst_file: After getting top and left matrices
# ##     create a content of param files; The obtained content will be used
# ##     by the file_manager to create instance file.
# ##
# ##  3. transform_left_matrix, transform_top_matrix: moves clues of these
# ##     matrices from the top to down in the top matrix, and from left to
# ##     right of the left matrix. Also it removes the empty rows in
# ##     the top matrix and empty columns in the left matrix. As well as
# ##     it returns the number of all clues in respective matrix.
# ##
# ##  4. display_instance: is the function which generates display the any
# ##     instance of Nonogram on the traditional way so that it would be possible
# ##     to print it out and test with participants.
# ##
# ##  5. count_total_clues: this will just count the total number of clues of the
# ##     instance from both matrices: top and left. This is will be needed for
# ##     the evaluation part of the project.
# ##
# # ##########################################################################


from matrix_processor import *
import matplotlib.pylab as plt


PARAMS_TEMPLATE = """\
language ESSENCE' 1.0
letting mainRow be {row_count}
letting mainCol be {col_count}
letting topRows be {top_row_count}
letting leftCols be {left_col_count}
letting leftMatrix be [
{left_matrix_content}]
letting topMatrix be [
{top_matrix_content}
]"""


def create_top_matrix(solution_board):
    #  amount of rows:
    topRows = len(solution_board)
    topRows = topRows / 2 if topRows % 2 == 0 else topRows / 2 + 1

    #  amount of cols: len(solution_board[0])
    topMatrix = [[0 for x in range(len(solution_board[0]))] for y in range(topRows)]

    for i in range(0, len(solution_board[0])):  # columns
        counter = 0
        index = 0
        for j in range(0, len(solution_board)):
            if solution_board[j][i] == 1:
                counter += 1
                if topMatrix[index][i] != 0 and j > 0 and solution_board[j - 1][i] == 0 and index < topRows:
                    index += 1
                topMatrix[index][i] = counter
            else:
                counter = 0
                if index > 0 and topMatrix[index][i] > 0:
                    index += 1
    return topMatrix


def create_left_matrix(solution_board):
    #  amount of columns:
    leftCols = len(solution_board[0])
    leftCols = leftCols / 2 if leftCols % 2 == 0 else leftCols / 2 + 1

    #  amount of rows: len(solution_board)
    leftMatrix = [[0 for x in range(leftCols)] for y in range(len(solution_board))]

    for i in range(0, len(solution_board)):
        counter = 0
        index = 0
        for j in range(0, len(solution_board[0])):
            if solution_board[i][j] == 1:
                counter += 1
                if leftMatrix[i][index] != 0 and j > 0 and solution_board[i][j - 1] == 0 and index < leftCols:
                    index += 1
                leftMatrix[i][index] = counter
            else:
                counter = 0
                if(index > 0 and leftMatrix[i][index] > 0):
                    index += 1
    return leftMatrix


def create_content_of_inst_file(solution_board):
    left_matrix = create_left_matrix(solution_board)
    top_matrix = create_top_matrix(solution_board)
    left_matrix_content = convert_matrix_to_s(left_matrix)
    top_matrix_content = convert_matrix_to_s(top_matrix)
    return PARAMS_TEMPLATE.format(
        left_matrix_content=left_matrix_content,
        top_matrix_content=top_matrix_content,
        row_count=len(solution_board),
        col_count=len(solution_board[0]),
        top_row_count=len(top_matrix),
        left_col_count=len(left_matrix[0]),
    )


def transform_left_matrix(solution_board):
    left_matrix = create_left_matrix(solution_board)

    temp_matrix = [[0 for x in range(len(left_matrix[0]))]for y in range(len(left_matrix))]
    max_len = 0
    total_amount_digits = 0
    for row in range(0, len(left_matrix)):
        temp_matrix[row] = filter(lambda x: x != 0, left_matrix[row])
        # this is needed to truncate the matrix from empty columns
        if max_len < len(temp_matrix[row]):
            max_len = len(temp_matrix[row])

    transformed_left_matrix = [[0 for x in range(max_len)]for y in range(len(left_matrix))]
    for row in range(0, len(temp_matrix)):
        for col in range(0, len(temp_matrix[row])):
            transformed_left_matrix[row][len(transformed_left_matrix[row]) - (1 + col)] = temp_matrix[row][len(temp_matrix[row]) - (1 + col)]
            total_amount_digits += 1
    return transformed_left_matrix, total_amount_digits


def transform_top_matrix(solution_board):
    top_matrix = create_top_matrix(solution_board)

    temp_converted_1 = [[0 for x in range(len(top_matrix))]for y in range(len(top_matrix[0]))]
    temp_converted_2 = [[0 for x in range(len(top_matrix))]for y in range(len(top_matrix[0]))]
    max_len = 0
    total_amount_digits = 0

    for col in range(0, len(top_matrix[0])):
        for row in range(0, len(top_matrix)):
            temp_converted_1[col][row] = top_matrix[row][col]

    for row in range(0, len(temp_converted_1)):
        for col in range(0, len(temp_converted_1[0])):
            temp_converted_2[row] = filter(lambda x: x != 0, temp_converted_1[row])
        # cleaning the top matrix from row which are completely emty
        if max_len < len(temp_converted_2[row]):
            max_len = len(temp_converted_2[row])

    transformed_top_matrix = [[0 for x in range(len(top_matrix[0]))]for y in range(max_len)]
    for col in range(0, len(temp_converted_2)):
        for row in range(0, len(temp_converted_2[col])):
            transformed_top_matrix[len(transformed_top_matrix) - (1 + row)][col] = temp_converted_2[col][len(temp_converted_2[col]) - (1 + row)]
            total_amount_digits += 1
    return transformed_top_matrix, total_amount_digits


def display_instance(solution_board, title):
    left_matrix, num_clues_cols = transform_left_matrix(solution_board)
    top_matrix, num_clues_rows = transform_top_matrix(solution_board)
    # print(num_clues_cols)
    # print(num_clues_rows)

    # replacing all zeros with empty string
    # print_matrix(left_matrix)
    # print_matrix(top_matrix)
    # print_matrix(solution_board)
    for i in range(0, len(left_matrix)):
        for j in range(0, len(left_matrix[0])):
            if left_matrix[i][j] == 0:
                left_matrix[i][j] = ''

    for i in range(0, len(top_matrix)):
        for j in range(0, len(top_matrix[0])):
            if top_matrix[i][j] == 0:
                top_matrix[i][j] = ''
    empty_sol_board = [[''] * len(solution_board[0])] * len(solution_board)
    w = 10
    h = 10
    fig = plt.figure(figsize=(w, h))
    plt.axis('off')
    # for bbox = [x, y, width, height]
    # left matrix's:
    x0 = 0.1
    y0 = 0.1
    width0 = 0.025 * len(left_matrix[0])
    height0 = 0.025 * len(left_matrix)
    # top matrix's:
    x1 = 0.1 + 0.025 * len(left_matrix[0])
    y1 = 0.1 + 0.025 * len(left_matrix)
    width1 = 0.025 * len(top_matrix[0])
    height1 = 0.025 * len(top_matrix)
    # solution board matrix's:
    x3 = 0.1 + 0.025 * len(left_matrix[0])
    y3 = 0.1
    width3 = 0.025 * len(empty_sol_board[0])
    height3 = 0.025 * len(empty_sol_board)

    plt.table(cellText=left_matrix, cellLoc='center', bbox=[x0, y0, width0, height0])
    plt.table(cellText=top_matrix, cellLoc='center', bbox=[x1, y1, width1, height1])
    plt.table(cellText=empty_sol_board, bbox=[x3, y3, width3, height3])

    plt.table(cellText=[['']], bbox=[x1, y1, 0.025 * len(top_matrix[0]), 0.001])
    plt.table(cellText=[['']], bbox=[x3, y3, 0.002, 0.025 * len(left_matrix)])
    plt.title(title)
    plt.show()


def count_total_clues(solution_board):
    left_matrix, num_clues_cols = transform_left_matrix(solution_board)
    top_matrix, num_clues_rows = transform_top_matrix(solution_board)
    total_num_clues = num_clues_cols + num_clues_rows
    return total_num_clues
