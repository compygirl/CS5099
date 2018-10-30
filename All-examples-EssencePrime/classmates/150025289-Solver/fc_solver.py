import operator
import time
class Forward_Checking_Solver():
    def __init__(self, given_problem, heuristics=None, d_way=False):
        self.problem = given_problem
        self.max_depth = len(self.problem.vars)
        self.ops = {'>': operator.gt,
           '<': operator.lt,
           '>=': operator.ge,
           '<=': operator.le,
           '=': operator.eq,
           '!=': operator.ne}
        self.start_time = time.time()
        self.node_size = 0
        self.revise_size = 0
        #Heuristics
        self.heuristics = heuristics
        self.sort_unassigned = self.sort_unassigned_static
        if(heuristics is not None):
            self.heuristics.set_heuristic_values()
            if(self.heuristics.is_dynamic == True):
                self.sort_unassigned = self.sort_unassigned_dynamic
        #unassigned
        self.unassigned = []
        for var in self.problem.vars:
            self.unassigned.append(self.problem.vars[var])
        #self.unassigned = sorted(self.unassigned)
        self.sort_unassigned_static(self.unassigned)
        if(d_way == True):
            self.checker = self.checker_d_branch
        else:
            self.checker = self.checker_2_branch

    def work(self):
        result = self.checker(self.unassigned)
        self.time_taken = time.time() - self.start_time
        if (result == True):
            #print("Time taken:", self.time_taken, "with", self.node_size, "nodes and", self.revise_size, "arc revisions")
            return self.node_size, self.time_taken, self.revise_size
        else:
            return None

    def work_optimisation(self):
        #stat from 1 and iterate through upwards
        min_value = 1
        result = None
        while(result == None):
            self.adjust_domains(min_value)
            result = self.work()
            min_value = min_value + 1
        print("Min solution is found in", min_value - 1)
        return result

    def adjust_domains(self, min_value):
        #adjust domains of variabes for min optim problem.
        for var in self.problem.vars:
            self.problem.vars[var].domain = []
            for i in range(0, min_value):
                self.problem.vars[var].domain.append(i)

    def checker_d_branch(self, un_vars):
        self.node_size = self.node_size + 1
        var = un_vars.pop(0)
        #assigning in d-way style
        for d in var.domain:
            var.change_value(d)
            #next ones
            #put prunes in a dict in case we need to undo them
            prunes = dict()
            consistent_flag = self.revise_future_arcs(un_vars, var, prunes)
            if(consistent_flag == True):
                if(len(un_vars) != 0):
                    result = self.checker_d_branch(un_vars)
                    if(result == True):
                        return result
                else:
                    return True
            #undo prunning
            self.undo_prunes(prunes)
        var.change_value(None)
        un_vars.append(var)
        self.sort_unassigned(un_vars)
        return False

    def checker_2_branch(self, un_vars):

        if(len(un_vars) == 0):
            return True
        self.node_size = self.node_size + 1
        # if (self.node_size < 7):
        #     print(self.problem)
        var = un_vars.pop(0)
        val = var.domain[0]
        result = self.checker_2_branch_left(un_vars, var, val)
        if(result == True):
            return result
        result = self.checker_2_branch_right(un_vars, var, val)
        if(result == True):
            return result
        return False

    def checker_2_branch_left(self, un_vars, var, val):
        var.change_value(val)
        prunes = dict()
        consistent_flag = self.revise_future_arcs(un_vars, var, prunes)
        if (consistent_flag == True):
            result = self.checker_2_branch(un_vars)
            if (result == True):
                return result
        self.undo_prunes(prunes)
        var.change_value(None)
        un_vars.append(var)
        self.sort_unassigned(un_vars)
        return False

    def checker_2_branch_right(self, un_vars, var, val):
        var.domain.remove(val)
        prunes = dict()
        if(len(var.domain) != 0):
            consistent_flag = self.revise_future_arcs(un_vars, var, prunes)
            if (consistent_flag == True):
                result = self.checker_2_branch(un_vars)
                if (result == True):
                    return result
            self.undo_prunes(prunes)
        var.domain.append(val)
        var.domain = sorted(var.domain)
        return False

    def revise_future_arcs(self, un_vars, var, prunes):
        local_vars = []
        for el in un_vars:
            local_vars.append(el)
        consistent_flag = True
        while(consistent_flag and len(local_vars) != 0):
            future = local_vars.pop(0)
            consistent_flag, reduction_flag = self.revise_arc(future, var, prunes)
        self.sort_unassigned(un_vars)
        return consistent_flag

    def revise_arc(self, v_f, v_d, prunes):
        #constraint can be f,d or d,f in constraint list.
        #check both cases.
        constraint_flag = False
        if (v_f, v_d) in self.problem.constraints:
            constraint_flag = True
            op = self.problem.constraints[(v_f,v_d)]
            #keep the constraint order
            order = 0
        elif (v_d, v_f) in self.problem.constraints:
            constraint_flag = True
            op = self.problem.constraints[(v_d,v_f)]
            order = 1
        if (constraint_flag == True):
            self.revise_size = self.revise_size + 1
            #if value is set, revise depending on that
            if(v_d.value is not None):
                new_domain = self.revise_arc_on_value(v_d, v_f, op, order)
            #otherwise, check domain
            else:
                new_domain = self.revise_arc_on_domain(v_d, v_f, op, order)
            #if the domain decreased
            if(len(new_domain) != len(v_f.domain)):
                #wipeout control
                if(len(new_domain) != 0):
                    if v_f.id not in prunes:
                        prunes[v_f.id] = []
                    #add pruned values to the prune map.
                    for lost in v_f.domain:
                        if(lost not in new_domain):
                            prunes[v_f.id].append(lost)
                    v_f.domain = new_domain
                    return True, True
                else:
                    return False, False
            #if no change in domain, consistent
            return True, False
        #if no constraint, consistent
        else:
            return True, False

    def revise_arc_on_value(self, v_d, v_f, op, order):
        #create another domain from scratch (dont want same pointer)
        new_domain = []
        for el in v_f.domain:
            new_domain.append(el)
        for el in v_f.domain:
            #constraints
            if(order == 0):
                comp = (el, v_d.value)
            elif(order == 1):
                comp = (v_d.value, el)
            #apply the operator the check it's verified. (supported)
            if(self.ops[op](comp[0], comp[1]) == False):
                new_domain.remove(el)
        return new_domain

    def revise_arc_on_domain(self, v_d, v_f, op, order):
        #create another domain from scratch (dont want same pointer)
        new_domain = []
        for el in v_f.domain:
            new_domain.append(el)
        for el in v_f.domain:
            support_flag = False
            for sup in v_d.domain:
                #if one element from d support el, raise the flag
                if(order == 0):
                    comp = (el, sup)
                elif(order == 1):
                    comp = (sup, el)
                #apply the operator the check it's verified. (supported)
                if(self.ops[op](comp[0], comp[1]) == True):
                    support_flag = True
            if(support_flag == False):
                new_domain.remove(el)
        return new_domain

    def undo_prunes(self, prunes):
        #undo prunning
        for pruned in prunes:
            self.problem.vars[pruned].domain.extend(prunes[pruned])
            self.problem.vars[pruned].domain.sort()
        prunes = dict()

    def sort_unassigned_static(self, un_vars):
        un_vars.sort()

    def sort_unassigned_dynamic(self, un_vars):
        self.heuristics.update_heuristic_values(un_vars)
        self.sort_unassigned_static(un_vars)
