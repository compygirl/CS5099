class Problem():
    def __init__(self):
        self.vars = dict()
        self.constraints = dict()

class Graph_Coloring_Problem(Problem):
    def __init__(self, given_vars, given_edges):
        Problem.__init__(self)
        self.vars = given_vars
        self.edges = given_edges
        self.define_constraints()

    def define_constraints(self):
        for edge in self.edges:
            constraint = '!='
            self.constraints[edge] = constraint

    def __str__(self):
        result = "\n"
        for var in self.vars:
            result += "V_" + str(self.vars[var].id[1]) + " = " + str(self.vars[var].value)
            result = result + "\n"
        return result

class Futoshiki_Problem(Problem):
    def __init__(self, given_vars, given_inequalities):
        Problem.__init__(self)
        self.size = 5
        self.vars = given_vars
        self.inequal = given_inequalities
        self.define_constraints()

    def define_constraints(self):
        #row is different
        for row in range(self.size):
            for i in range(self.size):
                for j in range(self.size):
                    # we don't wanna add i,j and j,i twice and we don't wanna add i,i
                    if(i<j):
                        constraint = '!='
                        v_1 = self.vars[(row, i)]
                        v_2 = self.vars[(row, j)]
                        self.constraints[(v_1, v_2)] = constraint
        #column is different
        for column in range(self.size):
            for i in range(self.size):
                for j in range(self.size):
                    # we don't wanna add i,j and j,i twice and we don't wanna add i,i
                    if(i<j):
                        constraint = '!='
                        v_1 = self.vars[(i, column)]
                        v_2 = self.vars[(j, column)]
                        self.constraints[(v_1, v_2)] = constraint
        for inequality in self.inequal:
            self.constraints[inequality] = self.inequal[inequality]

    def __str__(self):
        result = "\n"
        for i in range(self.size):
            for j in range(self.size):
                result = result + str(self.vars[(i,j)]) + " "
            result = result + "\n"
        return result

class Sudoku_Problem(Problem):
    def __init__(self, given_matrix):
        Problem.__init__(self)
        self.size = 9
        self.vars = given_matrix
        self.define_constraints()
    def define_constraints(self):
        #row is different
        for row in range(self.size):
            for i in range(self.size):
                for j in range(self.size):
                    # we don't wanna add i,j and j,i twice and we don't wanna add i,i
                    if(i<j):
                        constraint = '!='
                        v_1 = self.vars[(row, i)]
                        v_2 = self.vars[(row, j)]
                        self.constraints[(v_1, v_2)] = constraint
        #column is different
        for column in range(self.size):
            for i in range(self.size):
                for j in range(self.size):
                    # we don't wanna add i,j and j,i twice and we don't wanna add i,i
                    if(i<j):
                        constraint = '!='
                        v_1 = self.vars[(i, column)]
                        v_2 = self.vars[(j, column)]
                        self.constraints[(v_1, v_2)] = constraint
        #3x3 blocks
        for i in range(int(self.size/3)):
            for j in range(int(self.size/3)):
                for row1 in range(3):
                    for row2 in range(3):
                        for col1 in range(3):
                            for col2 in range(3):
                                if(row1 < row2 and col1 != col2):
                                    constraint = '!='
                                    v_1 = self.vars[(row1+i*3, col1+j*3)]
                                    v_2 = self.vars[(row2+i*3, col2+j*3)]
                                    self.constraints[(v_1, v_2)] = constraint

    def __str__(self):
        result = "\n"
        for i in range(self.size):
            for j in range(self.size):
                result = result + str(self.vars[(i,j)]) + " "
            result = result + "\n"
        return result
