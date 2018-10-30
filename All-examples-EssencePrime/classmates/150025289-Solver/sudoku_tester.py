import copy
import numpy as np

from problem import Sudoku_Problem
from variable import Variable
from fc_solver import Forward_Checking_Solver
from mac_solver import MAC_Solver
from heuristics import *

class Sudoku_Tester():
    def __init__(self):
        self.files = []
        self.testers = []
        self.results = dict()
        self.files.append("sudoku_instances/s00.txt")
        for i in range(1,16):
            name = "sudoku_instances/s" + str(i).zfill(2)
            self.files.append(name + "a.txt")
            self.files.append(name + "b.txt")
            self.files.append(name + "c.txt")
        self.files.append("sudoku_instances/s16.txt")

        for i in range(len(self.files)):
            self.testers.append(Sudoku_File_Tester(self.files[i]))

    def test_all(self):
        for i in range(len(self.testers)):
            print("-"*50)
            print("Test:", self.files[i])
            print("-"*50)
            self.results[self.files[i]] = self.testers[i].test()
        print("-"*50)

    def pretty_print_results(self):
        self.pretty_print(self.results)

    def pretty_print(self, d, indent=0):
       for key, value in d.items():
          print ('\t' * indent + str(key))
          if isinstance(value, dict):
             self.pretty_print(value, indent+1)
          else:
             print ('\t' * (indent+1) + str(value))

class Sudoku_File_Tester():
    def __init__(self, sudoku_filename):
        self.filename = sudoku_filename

    def test(self):
        self.results = dict()
        print("FC Test started")
        print("-"*50)
        self.results["Forward_Checking_Solver"] = self.test_solver(Forward_Checking_Solver)
        print("-"*50)
        print("MAC Test started")
        print("-"*50)
        self.results["MAC_Solver"] = self.test_solver(MAC_Solver)
        return self.results

    def test_solver(self, solver_name):
        matrix = dict()
        results = dict()
        with open(self.filename, 'r') as f:
            for i in range(9):
                l = f.readline()
                for j in range(9):
                    matrix[(i,j)] = Variable((i,j), None, [1,2,3,4,5,6,7,8,9])
                    value = int(l.split(" ")[j])
                    if(value != 0):
                        matrix[(i,j)].force_value(value)

        sp_empty = Sudoku_Problem(matrix)
        sp_no = copy.deepcopy(sp_empty)
        sp_degree = copy.deepcopy(sp_empty)
        sp_card = copy.deepcopy(sp_empty)
        sp_min_dom = copy.deepcopy(sp_empty)
        sp_brelaz = copy.deepcopy(sp_empty)

        h_degree = Maximum_Degree_Heuristics(sp_degree)
        h_min_dom = Minimum_Domain_Heuristics(sp_min_dom)
        h_card = Maximum_Cardinality_Heuristics(sp_card)
        h_brelaz = Brelaz_Heuristics(sp_brelaz)

        print("No Heuristics test started")
        fc_no = Forward_Checking_Solver(sp_no)
        results["None"] = fc_no.work()

        print("Degree Heuristics test started")
        fc_degree = Forward_Checking_Solver(sp_degree, heuristics=h_degree)
        results["Degree"] = fc_degree.work()


        print("Cardinality Heuristics test started")
        fc_card = Forward_Checking_Solver(sp_card, heuristics=h_card)
        results["Cardinality"] = fc_card.work()


        print("Min-Domain Heuristics test started")
        fc_min_dom = solver_name(sp_min_dom, heuristics=h_min_dom)
        results["Min-Domain"] = fc_min_dom.work()

        print("Brelaz Heuristics test started")
        fc_brelaz = solver_name(sp_brelaz, heuristics=h_brelaz)
        results["Brelaz"] = fc_brelaz.work()

        return results
