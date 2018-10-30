import operator
import time
from fc_solver import Forward_Checking_Solver
class MAC_Solver(Forward_Checking_Solver):
    def __init__(self, given_problem, heuristics=None, d_way=False):
        Forward_Checking_Solver.__init__(self, given_problem, heuristics, d_way)


    def revise_future_arcs(self, un_vars, var, prunes):
        arc_list = set()
        for future in un_vars:
            arc_list.add((var, future))
        consistent_flag = True
        while(consistent_flag and len(arc_list) != 0):
            var_cur, var_fut = arc_list.pop()
            consistent_flag, reduction_flag = self.revise_arc(var_fut, var_cur, prunes)
            if(reduction_flag):
                for var_fut_2 in un_vars:
                    if(var_fut_2 != var_cur):
                        arc_list.add((var_fut, var_fut_2))
        self.sort_unassigned(un_vars)
        return consistent_flag
