from skimage.io import imread
# from skimage.io import imread_collection
import os, os.path, time
from skimage.transform import resize
from matplotlib import pyplot as plt
import matplotlib.cm as cm

# egImage = "http://img-aws.ehowcdn.com/600x600p/photos.demandstudios.com/179/217/fotolia_6885823_XS.jpg"
# # if the argument as_grey is true -> greay colour, otherwise it is colourful:
# img = imread(egImage, as_grey = False)
# plt.imshow(img, cmap = cm.gray)
# plt.show()



# egImage = "imagesInstances/Second.jpg"
# # if the argument as_grey is true -> greay colour, otherwise it is colourful:
# img = imread(egImage, as_grey = True)
# img2= img[1:5, 0:6]
#
# plt.imshow(img2, cmap = cm.gray)
# plt.show()
#
# print(str(img.shape))
# print(type(img))
#
# plt.imshow(img, cmap = cm.gray)
# plt.show()


# didn't work : collection of images
# col_dir = 'imagesInstances/*.jpg'
# col = imread_collection(col_dir)
# for image in col_dir:
#     imageAct = imread(image, as_grey = True)
#     plt.imshow(imageAct, cmap = cm.gray)
#     plt.show()
folder = "imagesInstances"
for filename in os.listdir(folder):
    img = imread(os.path.join(folder, filename), as_grey = True)
    plt.imshow(img, cmap = cm.gray)
    plt.show()
    # if file.endswith(".jpg"):
        # print str(file) + " - Creation date: " + str(time.ctime(os.path.getctime(file)))
        # img = imread(file, as_grey = True)
        # plt.imshow(img, cmap = cm.gray)
        # plt.show()




egImage = "imagesInstances/Smile.png"
# if the argument as_grey is true -> greay colour, otherwise it is colourful:
img = imread(egImage, as_grey = True)
print(img)
plt.imshow(img, cmap = cm.gray)
plt.show()
mainRows, mainCols = img.shape
solutionBoard=[[0 for x in range(mainCols)] for y in range(mainRows)]
topRows = mainRows/2 if mainRows%2==0 else mainRows/2+1
leftCols = mainCols/2 if mainCols%2==0 else mainCols/2+1

# topMatrix = [[0 for x in range(topRows)] for y in range(mainCols)]
# leftMatrix = [[0 for x in range(mainRows)] for y in range(leftCols)]

print ('{}{}\n{}{}'.format("rows: ", mainRows, "cols: ", mainCols))


line=""
# for i in range(0, mainRows):
#     for j in range(0, mainCols):
#         item = str(int(img[i, j]))
#         line=line+" "+item
#         line.lstrip()
#
#     print (line)
#     line=""
# print('\n')


for i in range(0, mainRows):
    for j in range(0, mainCols):
        solutionBoard[i][j] = int(round(img[i, j]))
        item = str(int(round(img[i, j])))
        line=line+" "+item
        line.lstrip()
    print (line)
    line=""

print('\n')

#checking the solutionBoard
for i in range(0, mainRows):
    for j in range(0, mainCols):
        line = line + " "+str(solutionBoard[i][j])
        line.lstrip()
    print (line)
    line = ""

# # create leftMatrix:
# for i in range(0, mainRows):
#     for j in range(0, mainCols):




# print(str(img.shape))
# print(type(img))
