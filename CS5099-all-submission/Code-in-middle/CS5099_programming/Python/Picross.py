from skimage.io import imread
# from skimage.transform import resize
import os
import string
from subprocess import call
import re
# import os.path

# from matplotlib import pyplot as plt
# import matplotlib.cm as cm
PARAMS_TEMPLATE = """\
language ESSENCE' 1.0
letting mainRow be {row_count}
letting mainCol be {col_count}
letting topRows be {top_row_count}
letting leftCols be {left_col_count}
letting leftMatrix be [
{leftMatrixContent}]
letting topMatrix be [
{topMatrixContent}
]"""

class Picross:

    def __init__(self, workingFolder):
        self.workingFolder = workingFolder

###########################################################################
# reading the images from the folder and
# returns the list of all images in the folder
###########################################################################

    def getListOfImages(self):
        imgStore = []
        for filename in os.listdir(self.workingFolder):
            # print(filename)
            if(filename.endswith(".jpg") or filename.endswith(".png")):
                img = imread(os.path.join(self.workingFolder, filename), as_grey = True)
                if img is not None:
                    imgStore.append(img)
            #displays the images
            # plt.imshow(img, cmap = cm.gray)
            # plt.show()
        return imgStore
###########################################################################
# Similar to the previous function: getListOfImages(),
# but returns the list of filenames for images:
###########################################################################
    def getFileNames(self):
        filenames=[]
        for filename in os.listdir(self.workingFolder):
            if(filename.endswith(".jpg") or filename.endswith(".png")):
                filename = filename.translate(None, string.punctuation)
                filenames.append(filename)
                # self.writeFile(filename)
        # print(filenames)
        return filenames




###########################################################################
# Get number of pictures:
###########################################################################

    def getNumPictures(self):
        imgStore = self.getListOfImages()
        return len(imgStore)

###########################################################################
# Get the sizes of each image and store in one big matrix for rows and columns
# Where the column 0 is for num of rows and column 1 for num of columns
###########################################################################

    def getInstanceDimensions(self):
        rows=self.getNumPictures()
        cols=2
        sizes=[[0 for x in range(cols)] for y in range(rows)]
        imgStore = self.getListOfImages()
        print(len(imgStore))
        rowIndex=0
        for img in imgStore:
            mainRows, mainCols = img.shape
            sizes[rowIndex][0]=mainRows
            sizes[rowIndex][1]=mainCols
            rowIndex+=1

        # for i in range(0, rows):
        #     print('{} {} {} {}'.format("rows: ",sizes[i][0],"cols:", sizes[i][1]))
        return sizes

###########################################################################
# creates the solutionBoard by converting the digits from the image to the solutionBoard
###########################################################################

    def createSolutionBoard(self, image, rows, cols):
        solutionBoard=[[0 for x in range(cols)] for y in range(rows)]

        for i in range(0, rows):
            for j in range(0, cols):
                if(int(round(image[i, j])) == 1):
                    solutionBoard[i][j] = 0
                else:
                    solutionBoard[i][j] = 1
        return solutionBoard

###########################################################################
# Puts all solutionBoards to the list and returns it.
###########################################################################

    def getSolBoardList(self):
        instNum = self.getNumPictures()
        sizes = self.getInstanceDimensions()
        imgStore = self.getListOfImages()
        filenames = self.getFileNames()

        solBoardList = []
        index = 0
        for img in imgStore:
            # Testing: printing the image
            print("image:")
            self.printImageAsMatrix(img, sizes[index][0], sizes[index][1])

            # part of the code:
            tempSolBoard= self.createSolutionBoard(img, sizes[index][0], sizes[index][1])

            content = self.createContent(tempSolBoard)
            filename = filenames[index]+".param"
            self.writeFile("paramFiles", filename, content)

            self.executeSavileRow(filename)

            tempLeftMatrix = self.createLeftMatrix(tempSolBoard)
            print("LeftMatrix:")
            self.printSolutionBoard(tempLeftMatrix)

            tempTopMatrix = self.createTopMatrix(tempSolBoard)
            print("TopMatrix:")
            self.printSolutionBoard(tempTopMatrix)

            # Testing: printing the solutionBoard
            print("solutionBoard:")
            self.printSolutionBoard(tempSolBoard)
            # self.createLeftMatrix(tempSolBoard)

            #part of the code:
            solBoardList.append(tempSolBoard)
            index+=1
        return solBoardList


###########################################################################
# Prints the image in the matrix binary representation
# imread represents black as 0 and white is 1.
###########################################################################

    def printImageAsMatrix(self, image, rows, cols):
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

###########################################################################
# Prints the solutionBoard in the matrix binary representation
# Must be opposite to the image, the black colour is 1 and white is 0.
###########################################################################

    def printSolutionBoard(self, solutionBoard):
        line=""
        for i in range(0, len(solutionBoard)):
            for j in range(0, len(solutionBoard[0])):
                line = line + " "+ str(solutionBoard[i][j])
                line.lstrip()
            print(line)
            line = ""

###########################################################################
###########################################################################
###########################################################################
# !!!! I need to test the content of this left Matrix!!!!!!!!!!!!!!!!
###########################################################################
###########################################################################
###########################################################################

    def createLeftMatrix(self, solutionBoard):
        #amount of columns:
        leftCols = len(solutionBoard[0])
        leftCols = leftCols/2 if leftCols%2==0 else leftCols/2+1

        #amount of rows: len(solutionBoard)
        leftMatrix=[[0 for x in range(leftCols)] for y in range(len(solutionBoard))]

        for i in range(0, len(solutionBoard)):#
            counter = 0
            index = 0
            for j in range(0, len(solutionBoard[0])):
                if solutionBoard[i][j]==1:
                    counter+=1
                    if leftMatrix[i][index] != 0 and j>0 and solutionBoard[i][j-1] == 0 and index<leftCols:
                        index+=1
                    leftMatrix[i][index]=counter
                else:
                    counter = 0
                    if(index>0 and leftMatrix[i][index]>0):
                        index+=1
        return leftMatrix


    def createTopMatrix(self, solutionBoard):
        #amount of rows:
        topRows = len(solutionBoard)
        topRows = topRows/2 if topRows%2==0 else topRows/2+1

        #amount of cols: len(solutionBoard[0])
        topMatrix = [[0 for x in range(len(solutionBoard[0]))] for y in range(topRows)]

        for i in range(0, len(solutionBoard[0])): # columns
            counter = 0
            index = 0
            for j in range (0, len(solutionBoard)):
                if solutionBoard[j][i]==1:
                    counter+=1
                    if topMatrix[index][i] != 0 and j>0 and solutionBoard[j-1][i]==0 and index<topRows:
                        index +=1
                    topMatrix[index][i] = counter
                else:
                    counter = 0
                    if index > 0 and topMatrix[index][i]>0:
                        index += 1
        return topMatrix




###########################################################################
# Write the file and the context of it.
###########################################################################

    def writeFile(self, path, filename, content):
        # path
        # path = '/paramFiles'
        completeName = os.path.join(path, filename)
        with open(completeName, 'w') as g:
            g.write(content)

###########################################################################
# Create several files/instances for each image:
###########################################################################

    def createInstanceFiles(self):
        # imgStore = self.getListOfImages()
        filenames = self.getFileNames()
        content  = self.createContent()

        for fnames in filenames:
            self.writeFile("/paramFiles",fnames+".param", "hello")

    def transMatrixTofileFormat(self, matrix):
        content = ""
        for row in matrix:
            content += str(row) + ",\n"
        # content += "]\n"
        # for i in range(0, len(matrix)):
        #     content+="["
        #     for j in range(0, len(matrix[0])):
        #         if j==len(matrix[0])-1:
        #             content+=str(matrix[i][j])+"], \n"
        #         else:
        #             content+=str(matrix[i][j])+", "
        # content+="]\n"
        return content




###########################################################################
# Create several files/instances for each image:
###########################################################################



    def createContent(self, solutionBoard):
        leftMatrix = self.createLeftMatrix(solutionBoard)
        topMatrix = self.createTopMatrix(solutionBoard)
        leftMatrixContent = self.transMatrixTofileFormat(leftMatrix)
        topMatrixContent = self.transMatrixTofileFormat(topMatrix)
        return PARAMS_TEMPLATE.format(
            leftMatrixContent = leftMatrixContent,
            topMatrixContent = topMatrixContent,
            row_count = len(solutionBoard),
            col_count = len(solutionBoard[0]),
            top_row_count = len(topMatrix),
            left_col_count = len(leftMatrix[0]),
        )

    def executeSavileRow(self, filename):
        call(["zsh", "-c", "savilerow", "picross_solver.eprime "+"ParamFiles/"+filename+" -run-solver -num-solutions 5 -solver-options '-varorder conflict -prop-node SACBounds'"])

    def readFile(self, path, filename):
        completeName = os.path.join(path, filename)
        with file(filename) as f:
            s=f.read()
        # print(s)
        # var=self.findWholeWord('SolverSolutionsFound:')(s)
        return s


    def readSolutionFiles(self, path):
        infoContentList = []
        numSolutions = []
        for filename in os.listdir(path):
            if(filename.endswith(".info")):
                s = self.readFile(path, filename)
                if s.find("SolverSolutionsFound:"):
                    print 'sucess'
                # print(type(s))
                infoContentList.append(s)
                # print(len(infoContentList))
        return infoContentList

    def findWholeWord(word):
        return re.compile(r'\b({0})\b'.format(word), flags=re.IGNORECASE).search
