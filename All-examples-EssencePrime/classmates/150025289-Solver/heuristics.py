import numpy as np
class Heuristics():
    def __init__(self, problem):
        self.problem = problem

class Maximum_Degree_Heuristics(Heuristics):
    def __init__(self, problem):
        Heuristics.__init__(self, problem)
        self.is_dynamic = False

    def set_heuristic_values(self):
        self.find_degrees()
        for var_key in self.problem.vars:
            # we are using minus because it's sorted in increasing order so, largest value will be minimum and in the front.
            self.problem.vars[var_key].heuristic_value = - self.problem.vars[var_key].static_degree + (self.problem.vars[var_key].int_id / (len(self.problem.vars) + 1))

    def find_degrees(self):
        for c in self.problem.constraints:
            #go through every constraint and increase the degree count
            c[0].static_degree = c[0].static_degree + 1
            c[1].static_degree = c[1].static_degree + 1


class Maximum_Cardinality_Heuristics(Heuristics):
    def __init__(self, problem):
        Heuristics.__init__(self, problem)
        self.cardinality = dict()
        self.assigned = []
        self.candidates = dict()
        self.is_dynamic = False

    def set_heuristic_values(self):
        #make it 0 first
        for var in self.problem.vars:
            self.problem.vars[var].heuristic_value = 0

        self.find_cardinalities()
        self.assign()
        #give values in decreasing order
        value = len(self.assigned)
        for i in range(len(self.assigned)):
            self.problem.vars[self.assigned[i]].heuristic_value = value
            value = value - 1

    def find_cardinalities(self):
        for var_key in self.problem.vars:
            self.cardinality[var_key] = []
        #creates a list of connections for each variable so we can trak which one is connected to which one
        for c in self.problem.constraints:
            self.cardinality[c[0].id].append(c[1].id)
            self.cardinality[c[1].id].append(c[0].id)

    def assign(self):
        #initialise every candidate with 0 connection to assigned group
        for var_key in self.problem.vars:
            self.candidates[var_key] = 0
        #random assignment in the beginning
        i = np.random.randint(9)
        j = np.random.randint(9)
        rand_id = (i, j)
        self.assigned.append(rand_id)
        for can_id in self.cardinality[rand_id]:
            self.candidates[can_id] = self.candidates[can_id] + 1
        #repeat until no variables left
        while(len(self.candidates)):
            #choose max connection to the assigned group (they are candidates)
            max_con = -1
            choosen_can = None
            for can_id in self.candidates:
                if (self.candidates[can_id] > max_con):
                    choosen_can = can_id
                    max_con = self.candidates[can_id]
            #add max connection to the assigned
            self.assigned.append(choosen_can)
            #remove choosen from the candidates, add new candidates and update old candidates
            self.candidates.pop(choosen_can)
            for can_id in self.cardinality[choosen_can]:
                #if they are already in the candidates increase
                if(can_id in self.candidates):
                    self.candidates[can_id] = self.candidates[can_id] + 1

class Minimum_Domain_Heuristics(Heuristics):
    def __init__(self, problem):
        Heuristics.__init__(self, problem)
        self.is_dynamic = True

    def set_heuristic_values(self):
        for var_key in self.problem.vars:
            domain_size = len(self.problem.vars[var_key].domain)
            self.problem.vars[var_key].heuristic_value = + domain_size + (self.problem.vars[var_key].int_id / (len(self.problem.vars) + 1))

    def update_heuristic_values(self, un_vars):
        for var in un_vars:
            self.update_heuristic_value_of_var(var)

    def update_heuristic_value_of_var(self, var):
        domain_size = len(var.domain)
        var.heuristic_value = + domain_size + (var.int_id / (len(self.problem.vars) + 1))

class Brelaz_Heuristics(Maximum_Degree_Heuristics, Minimum_Domain_Heuristics):
    def __init__(self, problem):
        Heuristics.__init__(self, problem)
        Maximum_Degree_Heuristics.find_degrees(self)
        self.is_dynamic = True

    def set_heuristic_values(self):
        for var_key in self.problem.vars:
            domain_size = len(self.problem.vars[var_key].domain)
            degree_value = self.problem.vars[var_key].static_degree
            self.problem.vars[var_key].heuristic_value = + domain_size - (degree_value / (len(self.problem.vars) + 1))

    def find_degrees_future(self, un_vars):
        for var in un_vars:
            var.future_degree = 0

        for var_1 in un_vars:
            for var_2 in un_vars:
                if(((var_1, var_2) in self.problem.constraints) or (var_2, var_1) in self.problem.constraints):
                    var_1.future_degree = var_1.future_degree + 1
                    var_2.future_degree = var_2.future_degree + 1


    def update_heuristic_values(self, un_vars):
        self.find_degrees_future(un_vars)
        for var in un_vars:
            domain_size = len(var.domain)
            var.heuristic_value = domain_size - (var.future_degree / (len(self.problem.vars) + 1))
