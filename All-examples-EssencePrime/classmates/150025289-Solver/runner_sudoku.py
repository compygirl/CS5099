from sudoku_tester import *
from problem import *
import pickle
import numpy as np
import matplotlib.pyplot as plt


t = Sudoku_Tester()
t.test_all()
t.pretty_print(results)

with open("results.dat", 'wb') as f:
    pickle.dump(t.results, f)

# with open("results.dat", 'rb') as f:
#     results = pickle.load(f)
