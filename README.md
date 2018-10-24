==========================================================================================
University of St Andrews
CS5099 - MSc AI Dissertation Software
==========================================================================================
AUTHOR: Aigerim Yessenbayeva
ID: 090017799
==========================================================================================
!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
!!!!!!!!!!!!!!!!!  NONOGRAM SOLVER with CONSTRAINT PROGRAMMING   !!!!!!!!!!!!!!!!!!!!!!!!
!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
==========================================================================================
GENERAL INFORMATION ABOUT THE PROGRAM:
=====================================
This program implements Nonogram puzzle game using Constraint Programming (CP) paradigm. The Constraint Satisfaction Problem (CSP) was implemented in Essence’ constraint programming language. Using Savile Row and Minion as tools of Essence’.

The creation of instances was implemented in Python2.7.13 with different approaches: 
(1)reading black and white images,
(2)random generation of instances,
(3) allowing user to set parameters of the instance, which they want. Such as size, percentage of the coloured cells.

In addition there were provided some abilities to print the Nonogram on paper and play with it.

As well as some analysis related to the type of the instance, whether it has 1 solution, more than 1 solutions, too many solutions or too difficult to solve it fast.

Based on this analysis it was approached to imeplement some algorithms which could generate different levels of the Nonogram and evaluate it in comparison with human rates. 
==========================================================================================
HARDWARE:

- Mac OS Sierra Version 10.12.6
————————————————————————————————————
REQUIRED SOFTWARE:

-SUBLIME or ATOM text editor for coding
-Download Minion 1.8 for Mac and unpack it: https://constraintmodelling.org/minion/ 
-Download Savile Row 1.6.5 for Mac and unpack it: http://savilerow.cs.st-andrews.ac.uk/
-Copy minion-1.8/bin/minion to overwrite bin/minion in your Savile Row directory
-Terminal
-Python 2.7.13
————————————————————————————————————

!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
!!!!!!!!!!!! HOW TO RUN THIS PROGRAM    !!!!!!!!!!!!
!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!

1. Place the “PicrossGeneratorSolver” in some directory
2. Open Terminal
3. Navigate to the “PicrossGeneratorSolver” directory
4. Type: Python main.py
5. The program is running
6. After it was completed go back to step 4 and repeat.
7. Play with different options in the main menu of the program: 
“Please choose the type of generating instances for Nanogram (insert number 1-7):
1. image based with - compare of 2 solutions
2. image based with - compare all solutions
3. random based with - compare 2 solutions
4. random based with - compare all solutions
5. fixed solution board - compare 2 solutions
6. fixed solution board - compare all solutions
7. create and display instances to print on paper and play with it
8. for participants
9. Test Minion”

====================================================
SOURCE CODE in “PicrossGeneratorSolver” directory:
====================================================
All files which are part of the program:
———————————————————————————————————————
PYTHON FILES:
————————————
1. main.py - main file to run
2. runner.py - essential file to structure the whole system
3. image_processor.py - uses skimage.io library of Python to read black and white images
4. file_manager.py - creates, writes, reads files for .param and for .csv formats
5. board_generator.py - generates boards in different ways: random,
6. instance_generator.py - creates needed format for the .param file + for displaying puzzle
7.solution_processor.py - implements the visualisation of the solution board to the Terminal.
8.single_solution.py - implements alg-s for reducing the number of solutions if it has >1
9. game_analyser.py - store all information about the game for Evaluation part
10. level_gen_helper.py - helps to generate different levels of Nonogram
11. matrix_processor.py - helps to print and convert needed matrices
12.constants.py - file which contains all the names of the constant variables, i.e.file and folder names 
——————————————————————————————————————————————————————————————————————————————————————————

ESSENCE’ FILE:
——————————————
1. picross_solver.eprime - essential model, of CSP which executed in game_analyser.py file
—————————————————————————————————————————————————————————————————————————————————————————

ADDITIONAL FOLDER & OUTPUTS:
————————————————————————————
1.image_instances - contains all black and white image files
2.param_files - where all .param files are generated and overwritten, when 1 solution found
3.participant_instances - all needed files and information after User_study: instances to be tested, results of timing in .csv files and implementation in R statistical programming languages for analysis of collected data in this study.
4. stat_data - folder which contains data of running instances and all their parameters and information of running by Savile Row. This folder should contain 3 .csv files:
(1)infoFile.csv - keeps the information of every file and its every single updates update after each execution without overwriting it.
(2)constInfoFile.csv - keeps the information of the instances which are set by the user. At one execution it has all instances of the same size and the same number of coloured cells.
(3)participants.csv - keeps the information of the instances, which were generated for the user study. in order to compare and analyse later.
—————————————————————————————————————————————————————————————————————————————————————————


