from sudoku_tester import *
from problem import *

v0 = Variable((0,0), None, [0])
v1 = Variable((0,1), None, [0])
v2 = Variable((0,2), None, [0])
v3 = Variable((0,3), None, [0])
v4 = Variable((0,4), None, [0])

vars = dict()
vars[v0.id] = v0
vars[v1.id] = v1
vars[v2.id] = v2
vars[v3.id] = v3
vars[v4.id] = v4

edges = dict()
edges[(v0,v1)] = 1
edges[(v0,v2)] = 1
edges[(v0,v3)] = 1
edges[(v0,v4)] = 1
edges[(v1,v2)] = 1
edges[(v2,v3)] = 1
edges[(v2,v4)] = 1
edges[(v3,v4)] = 1


gp = Graph_Coloring_Problem(vars, edges)
h = Brelaz_Heuristics(gp)
fc = Forward_Checking_Solver(gp, heuristics=h)
result = fc.work_optimisation()
print(gp)
