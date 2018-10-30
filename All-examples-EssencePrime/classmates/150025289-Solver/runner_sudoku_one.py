from sudoku_tester import *
from problem import *

matrix = dict()
results = dict()
with open("sudoku_instances/s12a.txt", 'r') as f:
    for i in range(9):
        l = f.readline()
        for j in range(9):
            matrix[(i,j)] = Variable((i,j), None, [1,2,3,4,5,6,7,8,9])
            value = int(l.split(" ")[j])
            if(value != 0):
                matrix[(i,j)].force_value(value)

sp = Sudoku_Problem(matrix)
h = Brelaz_Heuristics(sp)
fc = Forward_Checking_Solver(sp, heuristics=h)
result = fc.work()
print(sp)
print(result)
