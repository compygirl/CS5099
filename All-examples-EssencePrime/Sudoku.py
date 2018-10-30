# -*- coding: utf-8 -*-
"""
@author: 120003762

reference:
    https://github.com/ispartan301/SudokuSolver/blob/master/sudoku.py
    https://github.com/ispartan301/SudokuSolver/blob/master/CSP.py
    http://norvig.com/sudoku.html
    https://books.google.co.uk/books?id=1mJqCQAAQBAJ&pg=PA405&lpg=PA405&dq=dynamic+Brelaz+variable+ordering&source=bl&ots=egKsyg1HrM&sig=q3824BwKBRUXjl5AQMf28Nqqy0o&hl=en&sa=X&ved=0ahUKEwigr6qI9erSAhUMl5AKHVyGDkQQ6AEINzAD#v=onepage&q=dynamic%20Brelaz%20variable%20ordering&f=false
    https://www.codeproject.com/Articles/34403/Sudoku-as-a-CSP
    http://ktiml.mff.cuni.cz/~bartak/constraints/propagation.html

#use https://projecteuler.net/project/resources/p096_sudoku.txt
#to get sudoku puzzles
"""

import copy
import time
import random
import re

#from Tkinter import Tk
#from tkFileDialog import askopenfilename

###############################################################################
def main():
    #have user choose a Sudoku puzzle
#    filename = []
#    while (filename[-3:] != u'txt'):
#        print("Please select a txt file Sudoku Puzzle")
#        Tk().withdraw() 
#        filename = askopenfilename()
    filename = 'C:\Users\Admin\Documents\sudoku_puzzle_1.txt'
    sudoku = Sudoku(filename)
    #sudoku.show_sudoku_domains()
    #displaying variables in anaconda
    domains = sudoku.cell_domains
    names = sudoku.cell_names
    dict_of_neighbours = sudoku.peers
    quee_of_arcs = sudoku.all_arcs
    
    #use solver to solve puzzle
    csp = Solver(names, domains, dict_of_neighbours, quee_of_arcs)
    csp.solve_csp(initialize_csp = True)
    solution_domains = csp.csp_solutions
       
###############################################################################
"""
CLASS SUDOKU: used to import a sudoku csp file and intialize variable names 
(cell_names), variable domains (cell_domains), constraint connected cells (peers), 
and bidirectional arc constraints (all_arcs)
 
"""
class Sudoku(object): 
        
    #use https://projecteuler.net/project/resources/p096_sudoku.txt
    #to get sudoku puzzles
    def __init__(self,filename):
        self.rows = 'ABCDEFGHI' #label rows using [A-I]
        self.cols = '123456789' #label cols using [1-9]
        self.cell_names = [r+c for r in self.rows for c in self.cols]
        self.cell_domains = dict((n, self.cols) for n in self.cell_names)
        self.adjust_domains_to_fit_file(filename)
        self.peers = dict((n, self.find_peers(n)) for n in self.cell_names)
        self.all_arcs = self.find_all_arcs()
        
    def adjust_domains_to_fit_file(self, filename):
        i = 0
        for line in open(filename):
            for c in line.strip():
                if c in self.cols: 
                    self.cell_domains[self.cell_names[i]] = c
                i += 1
    
    def find_peers(self, cell_name): #find the cells linked to a cell (by constraints)
        peers = []
        #horizontal and vertical peers/constraints
        peers.extend([cell_name[0]+c for c in self.cols])
        peers.extend([r+cell_name[1] for r in self.rows])
        
        #sqrt(n) x sqrt(n) section peers/constraints
        row_section = ['ABC','DEF','GHI']
        col_section = ['123','456','789']                              
        row_i = [i for i in range(3) if re.search(cell_name[0], row_section[i])]
        col_i = [i for i in range(3) if re.search(cell_name[1], col_section[i])]
        peers.extend([r+c for r in row_section[row_i[0]] for c in col_section[col_i[0]]])
        set_peers = set(peers)
        set_peers.remove(cell_name[:2])
        return list(set_peers)                                
    
    def find_all_arcs(self):
        return [[x_i, x_j] for x_i in self.cell_names for x_j in self.peers[x_i]]
                                     
###############################################################################
"""
SOLVER CLASS: Used for solving csps. Supports variable heuristics mrv and brelaz.
Also supports a lcv (least constrained value) heuristic. Only one constraint 
type (ie. '!=', '==', '<' and etc) is supported per csp; cannot use multiple 
equality and inequality types at once. Finally, three csp solver methods:
forward_checking, constraint porpagation, and ac3 are supported. 
"""
class Solver(object):
    
    def __init__(self,cell_names, cell_domains, peers, all_arcs):
        self.names = cell_names
        self.domains = cell_domains
        self.dict_of_neighbours = peers
        self.quee_of_arcs = all_arcs
        self.csp_solutions = []
    
    def constraint(self, values_A, values_B, operation = '!='):
        #adjust values_A to satisfy operation with respect to values_B
        #operations supported: 
        #x = y - x is equal to y
        #x != y - x is not equal to y
        #x > y - x is greater than y
        #x < y - x is less than y
        #x >= y - x is greater than or equal to y
        #x <= y - x is less than or equal to y
        
        adj_A = ''
        if operation == '=':
            for a in values_A: 
                if a in values_B:
                    adj_A += a
        elif operation == '!=':
            if len(values_B) <= 1:
                for a in values_A: 
                    if a not in values_B:
                        adj_A += a
            else: adj_A = values_A
        elif operation == '>':
            min_B = int(values_B[0]) #because we use ordered string min is on the left
            i = 0
            for a in values_A:
                if int(a) > min_B:
                    adj_A = values_A[i:] #once again due to order can add everything after i
                    break
                i += 1
        elif operation == '>=':
            min_B = int(values_B[0]) 
            i = 0
            for a in values_A:
                if int(a) >= min_B:
                    adj_A = values_A[i:] 
                    break
                i += 1
        elif operation == '<':
            max_B = int(values_B[-1]) #because we use ordered string max is on the right
            for a in values_A:
                if int(a) < max_B:
                    adj_A += a 
        else:
            max_B = int(values_B[-1]) #because we use ordered string max is on the right
            for a in values_A:
                if int(a) <= max_B:
                    adj_A += a 
        
        return(adj_A)
    
    #--------------------------------------------------------------------------
    #HEURISTICS
    #reference: http://aima.cs.berkeley.edu/2nd-ed/newchap05.pdf
    #Variable Heuristics
    def mrv(self, cell_names, cell_domains, first_check = False):
        if first_check:
            max_domain_lenght = max(len(cell_domains[n]) for n in cell_names)
            minimum_remaining_values = [[n, cell_domains[n]] \
                                        for n in cell_names if len(cell_domains[n]) < max_domain_lenght] #initialized_cells
        else: 
            #find min domain length that is not one 
            min_domain_length = min(len(cell_domains[n]) for n in cell_names if len(cell_domains[n]) >= 2)
            minimum_remaining_values = [[n, cell_domains[n]] \
                                        for n in cell_names if len(cell_domains[n]) == min_domain_length]
    
        return (minimum_remaining_values)
    
    def random_mrv_cell_selector(self, cell_names, cell_domains): #choose random cell from mrv list (could do first value instead)
        minimum_remaining_values = self.mrv(cell_names, cell_domains)
        random_cell_index = random.randrange(0, len(minimum_remaining_values))
        return(minimum_remaining_values[random_cell_index])
        
    def brelaz(self, cell_names, cell_domains, peers): #find the mrv with the highest degree
        minimum_remaining_values = self.mrv(cell_names, cell_domains)
        maximum_degree, n  = max((self.calculate_degree(minimum_remaining_values[n][0], cell_domains, peers),n) \
                               for n in range(len(minimum_remaining_values)))
        maximum_degree_cell = minimum_remaining_values[n][0]
        return(maximum_degree_cell)
        
    def calculate_degree(self, cell_name, cell_domains, peers):
        #Debug
        #cell_name = minimum_remaining_values[0][0]
        degree = sum(len(cell_domains[p]) for p in peers[cell_name]) 
        return(degree)
    
    #Value Heuristics
#    def least_constrained_value(self, optimal_cell_name, cell_domains, peers):
#        lowest_count, i = min((self.calculate_value_peer_occurance(cell_domains[optimal_cell_name][i], optimal_cell_name, cell_domains, peers), i) \
#            for i in range(len(cell_domains[optimal_cell_name])))
#        least_constrained_val = cell_domains[optimal_cell_name][i]
#        return(least_constrained_val)
     
    def reorder_domain_per_lcv(self, optimal_cell_name, cell_domains, peers): #reorder domain per increasing lcv (left (low) - right (high))
        lcv_value_list = \
        [[self.calculate_value_peer_occurance( \
                cell_domains[optimal_cell_name][i], optimal_cell_name, cell_domains, peers \
                            ), cell_domains[optimal_cell_name][i]] for i in range(len(cell_domains[optimal_cell_name]))]
        #reorder domain using lcv_value_list
        sorted_lcv_list = sorted(lcv_value_list, key=lambda lcv_value: lcv_value[0])
        lcv_reordered_domain = ''
        for lcv_value in sorted_lcv_list:
            lcv_reordered_domain += lcv_value[1]
        return(lcv_reordered_domain)
    
    def calculate_value_peer_occurance(self, value, optimal_cell_name, cell_domains, peers): #handling values of strings
        occurances_count = len([n for n in peers[optimal_cell_name] if re.search(value, cell_domains[n])])
        return(occurances_count)
    
#    def select_first_value(optimal_cell_name, cell_domains): #select 1st value from domain in opt_cell
#        return(cell_domains[optimal_cell_name][0])
    
    #--------------------------------------------------------------------------
    #SEARCH TECHNIQUES/METHODS
    
    def forward_check(self, cell_name, cell_domains, peers, operation = '!='): # adjust domain of cell_name peers 
        adj_cell_domains = [[peer, self.constraint(cell_domains[peer], cell_domains[cell_name], operation)] \
                  for peer in peers[cell_name]]
        for adj_domain in adj_cell_domains:
            if (self.check_completeness(adj_domain[0], cell_domains, peers, operation) == False)  : #if domain 0 forward check fails - backtrack needed
                print('No Solution Found')
                return False
            cell_domains[adj_domain[0]] = adj_domain[1]
        
        return(cell_domains)
    
    def check_completeness(self, cell_name, cell_domains, peers, operation = '!='):
        if len(cell_domains[cell_name]) == 0:
            return False
        elif len(cell_domains[cell_name]) == 1 and operation == '!=':
            for peer in peers[cell_name]:
                if(cell_domains[cell_name] == cell_domains[peer]):
                    return False
        return True 
    
    #reference: http://norvig.com/sudoku.html
    #EXTENDS FORWARD CHECKING TO VARIABLES THAT HAVE LENGTH 1 AFTER A FORWARD CHECK
    def constraint_propagation(self, cell_name, cell_domains, peers, operation = '!='): #aka partially maintaining arc consistency
       cell_domains = self.forward_check(cell_name, cell_domains, peers, operation)   
       #Extending forward checking
       if cell_domains:
            cells_already_adjusted = []
            cells_already_adjusted.append(cell_name)
            peers_to_be_adjusted = self.find_peers_of_len_one(cell_name, cell_domains, peers)
            count = 0 #fail safe - increment to prevenet inf loops
            while(len(peers_to_be_adjusted)>0 and (count<500)):
                for name in peers_to_be_adjusted:
                    peers_to_be_adjusted.remove(name)
                    cell_domains = self.forward_check(name, cell_domains, peers, operation)
                    if not cell_domains:
                        #print('No Solution Found')
                        return False
                    cells_already_adjusted.append(name)
                    peers_of_len_one = self.find_peers_of_len_one(name, cell_domains, peers)
                    for peer in peers_of_len_one:
                        if (peer not in cells_already_adjusted):
                            peers_to_be_adjusted.append(peer)
                    
                count += 1 
            
            return(cell_domains)
       else:
           #print('No Solution Found')
           return False
    
    def find_peers_of_len_one(self, cell_name, cell_domains, peers):
        peers_of_len_one = [peer for peer in peers[cell_name] if len(cell_domains[peer]) == 1]
        return(peers_of_len_one)
       
    def find_all_cells_of_len_one(self, cell_names, cell_domains):
        return [name for name in cell_names if len(cell_domains[name]) == 1]
    
    def ac3(self, cell_domains, peers, all_arcs, operation = '!='):
        count = 0
        while(len(all_arcs)>0 and count < 3000):
            arc = all_arcs[0] #arc at front of quee
            old_x_i_domain = cell_domains[arc[0]] #domain of left arc node X_I
            new_x_i_domain = self.constraint(cell_domains[arc[0]], cell_domains[arc[1]], operation)
            all_arcs.remove(arc) #remove arc from quee of arcs
            
            if(len(new_x_i_domain) == 0):
                #print('No Solution Found')
                return False
            elif(len(old_x_i_domain) != len(new_x_i_domain)):
                cell_domains[arc[0]] = new_x_i_domain
                for peer in peers[arc[0]]:
                    if peer != arc[1] and ([peer, arc[0]] not in all_arcs): 
                       all_arcs.append([peer, arc[0]])        
            else: continue
        
            count += 1
        return(cell_domains)

    #--------------------------------------------------------------------------
    #SEARCH SOLVER - Implements backtracking
    def initial_constraint_propagation(self, operation = '!=', solver_description = 'forward_check'):
        #start by propagatting initialized contraints throughout csp 
        #ie adjust all domains to make them consistent with initialized csp domain specifications 
        initialized_cells = self.find_all_cells_of_len_one(self.names, self.domains)
        for init_cell in initialized_cells:
            succesfully_updated = self.solver(init_cell, operation, solver_description)
            if not succesfully_updated: return False
        return succesfully_updated
        
    def solver(self, cell_name, operation = '!=', solver_description = 'forward_check'):
        copy_domains = copy.deepcopy(self.domains)
        if (solver_description == 'forward_check'):
           self.domains =  self.forward_check(cell_name, self.domains, self.dict_of_neighbours, operation)
           if (self.domains == False): 
               self.domains = copy.deepcopy(copy_domains)
               return False
           if self.csp_solved(self.names, self.domains): 
               self.csp_solutions.append(self.domains)
               #print('CSP Solved')
        elif (solver_description == 'constraint_propagation'):
           self.domains =  self.constraint_propagation(cell_name, self.domains, self.dict_of_neighbours, operation)
           if (self.domains == False): 
               self.domains = copy.deepcopy(copy_domains)
               return False
           if self.csp_solved(self.names, self.domains): 
               self.csp_solutions.append(self.domains)
               #print('CSP Solved')
        else:
            self.domains =  self.ac3(self.domains, self.dict_of_neighbours, self.quee_of_arcs, operation)
            if (self.domains == False): 
                self.domains = copy.deepcopy(copy_domains)
                return False
            if self.csp_solved(self.names, self.domains): 
               self.csp_solutions.append(self.domains)
               #print('CSP Solved')
        
        return True

    def solve_csp(self, operation = '!=', solver_description = 'forward_check', \
               variable_heur = 'mrv', value_heur = 'lcv', initialize_csp = False):
        #######################################################################
        #                                                                     #
        #   Description of possible function input choices:                   #
        #   operation: see constraint function in class Solver                #
        #   note: solver only supports one constraint per csp ie.             #
        #   cannot use != & <=... can only use one                            #
        #   solver_description: 'forward_check', 'constraint_propagation',    #
        #   'ac3' - these specify the solver method                           #
        #   variable_heuristic: 'mrv', 'brelaz' - methods for choosing cells  #
        #   value_heuristic: 'none', 'lcv' - method for value selection       #
        #   initialize_csp: True/False -if initial problem propagation needed #
        #                                                                     #
        #######################################################################
        
        #first check if initial propagation needed and if initial propagation 
        #leads to a consistent (constraints satified) csp
        continue_program = True
        if initialize_csp: 
            continue_program = self.initial_constraint_propagation(operation, solver_description)
            if continue_program and self.csp_solved(self.names, self.domains): 
                print 'CSP SOLVED AFTER INITIAL CONSTRAINT PROPAGATION'
                return
        if not continue_program: return 
        
        #Solving CSP ----------------------------------------------------------
        #find variable to assign value to (cell whose values we will change whilst finding solution)
        if (variable_heur == 'mrv'):
            cell_to_be_explored = self.mrv(self.names, self.domains)[0]
        else: 
            cell_to_be_explored = self.brelaz(self.names, self.domains, self.dict_of_neighbours)
        #for readability purposes extract name and values form cell_to_be_explored
        cell_name = cell_to_be_explored[0]
        cell_value_domain = cell_to_be_explored[1]
        
        #reorder cell_value_domain to allow exploration from left to right per value_heurustic 
        if (value_heur == 'lcv'):
            cell_value_domain = self.reorder_domain_per_lcv(cell_name, self.domains, self.dict_of_neighbours)
        
        #make a copy of domains - done to allow backtracking using a recursive function  
        copy_domains = copy.deepcopy(self.domains)
        
        for value in cell_value_domain:
            #we use recursion and hence must make domains equal to their copy at the start 
            #of the for loop. This is done in case backtracking occurs.
            self.domains = copy.deepcopy(copy_domains)
        
            #assign value to call_name
            self.domains[cell_name] = value
            #implement solver on adjusted cell_domains
            succesfull_propagation = self.solver(cell_name, operation, solver_description)
            if not succesfull_propagation:
                #print 'Not succesfull with value ' + value 
                #print 'in cell ' + cell_name
                self.domains[cell_name] = cell_value_domain 
                continue
            if not self.csp_solved(self.names, self.domains):
                #print 'Succesfull with value ' + value 
                #print 'in cell ' + cell_name
                self.solve_csp(operation, solver_description, variable_heur, value_heur)
        
        return #may only give one solution as initial assignment (very 1st succesful value in 1st for loop) gives return
    
    def csp_solved(self, cell_names, cell_domains):
        return all((len(cell_domains[name]) == 1) for name in cell_names)
    
    def calculate_domain_space_size(self, cell_names, cell_domains):
        return sum(len(cell_domains[name]) for name in cell_names)

###############################################################################
#SUDOKU PRINTING FUNCTION
#reference: http://norvig.com/sudoku.html                                  
def show_sudoku_domains(cell_names, cell_domains, rows, cols):
    print
    width = 2+max(len(cell_domains[s]) for s in cell_names)
    #line = '+'.join(['.'*(width*3)]*3)
    line = '+'.join(['-'*(width*3)]*3)
    for r in rows:
        print ''.join(cell_domains[r+c].center(width)+('|' if c in '36' else '')
                  for c in cols)
        #if r not in 'CF': print line
        if r in 'CF': print line
    print

###############################################################################
#START PROGRAM FOR SOLVING SUDOKU PUZZLES  
if __name__ == '__main__':
    start = 'N'
    while start.lower() == 'n':                
        start = raw_input('Do you want to start the program? (y/n)\n') 
    #main()
    
    terminate_program = 'n'
    
    while terminate_program.lower() == 'n':
        #DEBUG if main does not excecute
        filename = 'C:\Users\Admin\Documents\sudoku_puzzle_1.txt'
        sudoku = Sudoku(filename)
        
        #extracting variables from file Sudoku
        domains = sudoku.cell_domains
        names = sudoku.cell_names
        dict_of_neighbours = sudoku.peers
        quee_of_arcs = sudoku.all_arcs
        #Display inital puzzle with domains
        print '\nThe following is a representation of the csp sudoku puzzle\n'
        show_sudoku_domains(names, domains,sudoku.rows, sudoku.cols)
        
        solver_method = raw_input('Choose a solver method:\n \
        f (forward_checking), cp (constraint_propagation), ac3 (arc_consistant) \n')
        
        #use solver to solve puzzle
        if solver_method.lower == 'f':
            #start with forward checking 
            csp_forward = Solver(names, domains, dict_of_neighbours, quee_of_arcs)
            start_time = time.time()
            csp_forward.solve_csp(initialize_csp = True)
            print("\nTime taken to solve:\n %s seconds \n" % (time.time() - start_time))
            solution_domains_forward = csp_forward.csp_solutions
            #display solution to sudoku puzzle
            show_sudoku_domains(names, solution_domains_forward[0],sudoku.rows, sudoku.cols)
        
        
        elif solver_method.lower == 'cp':
            #Use 'constraint_propagation'
            csp_cp = Solver(names, domains, dict_of_neighbours, quee_of_arcs)
            start_time = time.time()
            csp_cp.solve_csp(solver_description = 'constraint_propagation', initialize_csp = True)
            print("\nTime taken to solve:\n %s seconds \n" % (time.time() - start_time))
            solution_domains_cp = csp_cp.csp_solutions
            #display solution to sudoku puzzle
            show_sudoku_domains(names, solution_domains_cp[0],sudoku.rows, sudoku.cols)
        
        else:
            #Use 'ac3'
            csp_ac3 = Solver(names, domains, dict_of_neighbours, quee_of_arcs)
            start_time = time.time()
            csp_ac3.solve_csp(solver_description = 'ac3', initialize_csp = True)
            print("\nTime taken to solve:\n %s seconds \n" % (time.time() - start_time))
            solution_domains_ac3 = csp_ac3.csp_solutions
            #display solution to sudoku puzzle
            show_sudoku_domains(names, solution_domains_ac3[1],sudoku.rows, sudoku.cols)
        
        terminate_program = raw_input('\nDo you want to terminate the program? (y/n)\n') 