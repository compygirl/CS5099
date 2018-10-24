# ####################################################################################
# ## This module file deals with all images:
# ##  1. read_image: read provided images returning as a matrix of floating
# ##     point numbers, where each pixel is one number (white color is '~1'
# ##     and black is '~0');
# ##
# ##  2. display_image: displays read images to the screen (could be used for
# ##     testing);
# ##
# ##  3. imagename_to_paramname: transfers the image filename to the string
# ##     with '.param' extension;
# ##
# ##  4. print_binary_image: converts the read matrix of the image from the
# ##     floating point numbers to the integers;
# ##
# ##  5. get_rows_cols: get the number of rows and columns of the given image,
# ##     each pixel is 1 grid;
# ##
# ##  6. create_solution_board: creates solution board based on the obtained
# ##     matrix of the image, where now white color represented as '0' and
# ##     black as '1'.
# ####################################################################################

    
from skimage.io import imread
import os
import string

# ##### NEEDED FOR DISPLAY IMAGE#######
from matplotlib import pyplot as plt
import matplotlib.cm as cm


def read_image(directory, image_name):
    img = imread(os.path.join(directory, image_name), as_grey=True)
    return img


def display_image(image):
    plt.imshow(image, cmap=cm.gray)
    plt.show()


def imagename_to_paramname(image_filename):
    return image_filename.translate(None, string.punctuation) + ".param"


def print_binary_image(image):
    rows, cols = get_rows_cols(image)
    line = ""  # this is for printing
    for i in range(0, rows):
        for j in range(0, cols):
            pixel = str(int(round(image[i, j])))
            line = line + " " + pixel
            line.lstrip()
        print (line)
        line = ""
    print('\n')


def get_rows_cols(image):
    return image.shape


def create_solution_board(image):
    mainRows, mainCols = get_rows_cols(image)
    solution_board = [[0 for x in range(mainCols)] for y in range(mainRows)]
    for i in range(0, mainRows):
        for j in range(0, mainCols):
            if(int(round(image[i, j])) == 1):
                solution_board[i][j] = 0
            else:
                solution_board[i][j] = 1
    return solution_board
