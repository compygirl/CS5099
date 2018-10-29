from matrix_processor import *
from file_manager import *
# from image_processor import *

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
    # amount of rows:
    topRows = len(solution_board)
    topRows = topRows / 2 if topRows % 2 == 0 else topRows / 2 + 1

    # amount of cols: len(solution_board[0])
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
    # amount of columns:
    leftCols = len(solution_board[0])
    leftCols = leftCols / 2 if leftCols % 2 == 0 else leftCols / 2 + 1

    # amount of rows: len(solution_board)
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


def create_instance_file(solution_board, param_directory, param_filename):
    content = create_content_of_inst_file(solution_board)
    write_param_file(param_directory, param_filename, content)
