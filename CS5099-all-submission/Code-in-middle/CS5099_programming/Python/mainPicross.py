import Picross

class MainPicross:


    def main():
        game = Picross.Picross('imagesInstances') #instance of the class HMM
        # game.getImages()
        # print(game.getNumPictures())
        # game.getInstanceDimensions()

        #Good:
        game.getSolBoardList()
        # game.readFile("ParamFiles", "Smilejpg.param.info")

        # game.executeSavileRow()

        # game.writeFile("instance.param", "Hello World")
        # game.getListOfImages()
        # game.getFileNames()
        # game.createInstanceFiles()
        # game.createContent(5, 6)
        # game.createLeftMatrix()

        # print(game.readSolutionFiles('ParamFiles'))

    if __name__=='__main__':
        main()
