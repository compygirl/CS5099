import SolBoard

class Instance:
    def __init__(self, workingFolder, filename):
        self.workingFolder = workingFolder
        self.filename = filename
        # solBoard = SolBoard.SolutionBoard(workingFolder, filename)
        # solutionBoard = solBoard.createSolutionBoard()
        # solBoard.printMatrix(solutionBoard)

    def createTopMatrix(self):
        solBoard = SolBoard.SolutionBoard(self.workingFolder, self.filename)
        solutionBoard = solBoard.createSolutionBoard()

        # solutionBoard = self.solBoard.createSolutionBoard()

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


def printMatrix(matrix):
    line=""
    for i in range(0, len(matrix)):
        for j in range(0, len(matrix[0])):
            line = line + " "+ str(matrix[i][j])
            line.lstrip()
        print(line)
        line = ""
