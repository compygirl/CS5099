# import SolBoard
from Instance import printMatrix
from Instance import Instance

# class MainPicross:


    # def main():
        # game = SolBoard.SolutionBoard('imagesInstances', "flower.jpg") #instance of the class Picross
        # game.readImage()
        # game.displayImage()
        # print(game.getInstanceDimensions())
        # print(game.createSolutionBoard())
        # solutionBoard = game.createSolutionBoard()
        # game.printMatrix(solutionBoard)
        #########################################

# for :
    instance = Instance('imagesInstances', "flower.jpg")
    topMatrix = instance.createTopMatrix()
    printMatrix(topMatrix)

        # solBoard.printMatrix(topMatrix)
    # if __name__=='__main__':
    #     main()
