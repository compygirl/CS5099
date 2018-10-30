from sudoku_tester import *
from problem import *

matrix = dict()
for i in range(5):
    for j in range(5):
        matrix[(i,j)] = Variable((i,j), None, [1,2,3,4,5])

matrix[(1,0)].force_value(4)
matrix[(1,4)].force_value(2)
matrix[(2,2)].force_value(4)
matrix[(3,4)].force_value(4)
inequals = dict()
inequals[(matrix[(0,0)], matrix[(0,1)])] = ">"
inequals[(matrix[(0,2)], matrix[(0,3)])] = ">"
inequals[(matrix[(0,3)], matrix[(0,4)])] = ">"
inequals[(matrix[(3,3)], matrix[(3,4)])] = "<"
inequals[(matrix[(4,0)], matrix[(4,1)])] = "<"
inequals[(matrix[(4,1)], matrix[(4,2)])] = "<"

fp = Futoshiki_Problem(matrix, inequals)
h = Brelaz_Heuristics(fp)
fc = Forward_Checking_Solver(fp, heuristics=h)
result = fc.work()
print(fp)
