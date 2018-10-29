from skimage.io import imread
import os
import string
####### NEEDED FOR DISPLAY IMAGE#######
from matplotlib import pyplot as plt
import matplotlib.cm as cm

# import pyopencv as cv
# from ffnet import mlgraph, ffnet, tmlgraph, imlgraph
# import pylab
# import sys
# import cv,cv2
# import numpy
# cascade = cv.Load('C:\opencv\data\haarcascades\haarcascade_frontalface_alt.xml')

def read_image(directory, image_name):

    img = imread(os.path.join(directory, image_name), as_grey = True)

    # cv.imwrite("test.jpg", img)
    return img

def display_image(image):
    plt.imshow(image, cmap = cm.gray)
    plt.show()

def transfer_imagename_to_param(image_filename):
    return image_filename.translate(None, string.punctuation)+".param"

def print_binary_image(image):
    rows, cols = get_rows_cols(image)
    line="" # this is for printing
    for i in range(0, rows):
        for j in range(0, cols):
        #printing
            pixel = str(int(round(image[i, j])))
            line = line + " " + pixel
            line.lstrip()
        print (line)
        line=""
    print('\n')

def get_rows_cols(image):
    return image.shape

def create_solution_board(image):
    mainRows, mainCols = get_rows_cols(image)
    solutionBoard=[[0 for x in range(mainCols)] for y in range(mainRows)]
    for i in range(0, mainRows):
        for j in range(0, mainCols):
            if(int(round(image[i, j])) == 1):
                solutionBoard[i][j] = 0
            else:
                solutionBoard[i][j] = 1
    # solutionBoard[2][2] = 1
    # change_instance_file()
    return solutionBoard
#
# def create_image(solution_board):
#     cv.imwrite('test_image.jpg', solution_board)
