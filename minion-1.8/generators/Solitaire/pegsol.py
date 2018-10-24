#!/usr/bin/python

# english peg solitaire generator

# Copied from Andrea's E'

# 00000  1  2  3 00000
# 00000  4  5  6 00000
#  7  8  9 10 11 12 13 
# 14 15 16 17 18 19 20
# 21 22 23 24 25 26 27  
# 00000 28 29 30 00000 
# 00000 31 32 33 00000

import getopt, sys

noSteps=31
noFields=33
noMoves=76

cse=True   # do cse on the eq constraints between bState vars.


startPos=-1
useTest=False  # Use the test constraint.
useMin=False    # Use min instead of reify sumgeq
useReify=False  # use reify sumgeq

(optargs, other)=getopt.gnu_getopt(sys.argv, "", ["startpos=", "test", "min", "reify"])

for i in optargs:
    (a1, a2)=i
    if a1=="--startpos":
        startPos=int(a2)
    elif a1=="--test":
        useTest=True
    elif a1=="--min":
        useMin=True
    elif a1=="--reify":
        useReify=True
    else:
        print "Unrecognized option:%s" %(a1)
        sys.exit()




# 0 means no move. otherwise, transitionNumber[field1, field2]=moveNumber that moves a piece from f1 to f2. 
transitionNumber=\
             [ [0, 0, 1, 0, 0, 0, 0, 0, 2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0], \
               [0, 0, 0, 0, 0, 0, 0, 0, 0, 3, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0], \
               [4, 0, 0, 0, 0, 0, 0, 0, 0, 0, 5, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0], \
               [0, 0, 0, 0, 0, 6, 0, 0, 0, 0, 0, 0, 0, 0, 0, 7, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0], \
               [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0], \
               [0, 0, 0, 9, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,10, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0], \
               [0, 0, 0, 0, 0, 0, 0, 0, 12,0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,11, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0], \
               [0, 0, 0, 0, 0, 0, 0, 0, 0,13, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 14,0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0], \
               [18,0, 0, 0, 0, 0,15, 0, 0, 0, 17,0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,16, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0], \
               [0,19, 0, 0, 0, 0, 0,22, 0, 0, 0,20, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,21, 0, 0, 0, 0, 0, 0, 0, 0, 0], \
               [0, 0,23, 0, 0, 0, 0, 0,26, 0, 0, 0,24, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,25, 0, 0, 0, 0, 0, 0, 0, 0], \
               [0, 0, 0, 0, 0, 0, 0, 0, 0,27, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,28, 0, 0, 0, 0, 0, 0, 0], \
               [0, 0, 0, 0, 0, 0, 0, 0, 0, 0,30, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,29, 0, 0, 0, 0, 0, 0], \
               [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,31, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0], \
               [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,32, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0], \
               [0, 0, 0,34, 0, 0, 0, 0, 0, 0, 0, 0, 0,33, 0, 0, 0,36, 0, 0, 0, 0, 0, 0, 0, 0, 0,35, 0, 0, 0, 0, 0], \
               [0, 0, 0, 0,38, 0, 0, 0, 0, 0, 0, 0, 0, 0,37, 0, 0, 0,40, 0, 0, 0, 0, 0, 0, 0, 0, 0,39, 0, 0, 0, 0], \
               [0, 0, 0, 0, 0,42, 0, 0, 0, 0, 0, 0, 0, 0, 0,41, 0, 0, 0,44, 0, 0, 0, 0, 0, 0, 0, 0, 0,43, 0, 0, 0], \
               [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,45, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0], \
               [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,46, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0], \
               [0, 0, 0, 0, 0, 0,47, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,48, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0], \
               [0, 0, 0, 0, 0, 0, 0,50, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,49, 0, 0, 0, 0, 0, 0, 0, 0, 0], \
               [0, 0, 0, 0, 0, 0, 0, 0,52, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,51, 0, 0, 0,54, 0, 0, 0, 0, 0,53, 0, 0], \
               [0, 0, 0, 0, 0, 0, 0, 0, 0,56, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,55, 0, 0, 0,57, 0, 0, 0, 0, 0,58, 0], \
               [0, 0, 0, 0, 0, 0, 0, 0, 0, 0,60, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,59, 0, 0, 0,62, 0, 0, 0, 0, 0,61], \
               [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,64, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,63, 0, 0, 0, 0, 0, 0, 0, 0, 0], \
               [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,65, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,66, 0, 0, 0, 0, 0, 0, 0, 0], \
               [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,68, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,67, 0, 0, 0], \
               [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,69, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0], \
               [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,71, 0, 0, 0, 0, 0, 0, 0, 0, 0,70, 0, 0, 0, 0, 0], \
               [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,73, 0, 0, 0, 0, 0, 0, 0, 0, 0,72], \
               [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,74, 0, 0, 0, 0, 0, 0, 0, 0, 0], \
               [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,76, 0, 0, 0, 0, 0,75, 0, 0] \
             ]

# indexed by 0..75 for moves 1..76
transitionStep=[ [ 1,2,3], [1,4,9], [2,5,10], [3,2,1], [3,6,11],[4,5,6], [4,9,16], [5,10,17], [6,5,4], [6,11,18], \
                   [7,14,21], [ 7,8,9], [8,9,10], [8,15,22],[9,8,7], [9,16,23], [9,10,11], [9,4,1], [10,5,2], [10,11,12],                \
                    [10,17,24],  [10,9,8],   [11,6,3], [11,12,13],[11,18,25], [11,10,9], [12,11,10],[12,19,26],[13,20,27], [13,12,11],\
		   [14,15,16], [15,16,17],  [16,15,14], [16,9,4], [16,23,28], [16,17,18],[17,16,15], [17,10,5],[17,24,29], [17,18,19],\
                   [18,17,16], [18,11,6], [18,25,30],  [18,19,20], [19,18,17], [20,19,18], [21,14,7], [21,22,23], [22,23,24], [22,15,8],\
		   [23,22,21], [23,16,9],[23,28,31], [23,24,25],[24,23,22],[24,17,10],[24,25,26],[24,29,32], [25,24,23],[25,18,11],\
                   [25,30,33], [25,26,27], [26,25,24],[26,19,12],[27,20,13],[27,26,25],[28,29,30],[28,23,16],[29,24,17],[30,29,28],\
                   [30,25,18], [31,32,33], [31,28,23], [32,29,24], [33,32,31] ,[33,30,25]\
                  ]

def printpegsol(startField):
    print "MINION 3"
    
    print "**VARIABLES**"
    
    print "BOOL moves[%d, %d]"%(noSteps, noMoves)
    
    print "BOOL bState[%d, %d]"%(noSteps+1, noFields)
    
    if cse:
        print "BOOL equal[%d, %d]"%(noSteps, noFields)
    
    print "**SEARCH**"
    print "VARORDER [moves[_, _]]"
    print "VALORDER ["+reduce(lambda a,b: a+","+b, ["d" for i in range(noSteps) for j in range(noMoves)])
    print "]"
    
    print "**CONSTRAINTS**"
    
    if cse:
        for step in range(noSteps):
            for pos in range(noFields):
                print "reify(eq(bState[%d, %d], bState[%d, %d]), equal[%d, %d])"%(step, pos, step+1, pos, step, pos)
    
    # exactly one move
    for step in range(noSteps):
        print "sumleq([moves[%d, _]], 1)" % step
        print "sumgeq([moves[%d, _]], 1)" % step
    
    
    for step in range(noSteps):
        for f1 in range(noFields):
            for f2 in range(noFields):
                # If there is a valid transition
                if f1!=f2:
                    mv=transitionNumber[f1][f2]
                    if mv!=0:
                        mv=mv-1
                        middlefield=transitionStep[mv][1]-1
                        assert transitionStep[mv][0]==f1+1
                        assert transitionStep[mv][2]==f2+1
                        
                        # do the move iff the move variable is true.
                        if useTest:
                            print "test(["
                            print "bState[%d, %d], bState[%d, %d],"%(step, f1, step+1, f1)
                            print "bState[%d, %d], bState[%d, %d],"%(step, middlefield, step+1, middlefield)
                            print "bState[%d, %d], bState[%d, %d]"%(step, f2, step+1, f2)
                            print ", moves[%d, %d]])"%(step, mv)
                        elif useMin:
                            print "min(["
                            # six literals on bState vars.
                            print "bState[%d, %d], !bState[%d, %d],"%(step, f1, step+1, f1)
                            print "bState[%d, %d], !bState[%d, %d],"%(step, middlefield, step+1, middlefield)
                            print "!bState[%d, %d], bState[%d, %d]"%(step, f2, step+1, f2)
                            print "], moves[%d, %d])"%(step, mv)
                        else:
                            assert useReify
                            print "reify(sumgeq(["
                            # six literals on bState vars.
                            print "bState[%d, %d], !bState[%d, %d],"%(step, f1, step+1, f1)
                            print "bState[%d, %d], !bState[%d, %d],"%(step, middlefield, step+1, middlefield)
                            print "!bState[%d, %d], bState[%d, %d]"%(step, f2, step+1, f2)
                            print "], 6), moves[%d, %d])"%(step, mv)
                        
                        # frame axioms
                        # Nothing else changes.
                        staticfields=[]
                        
                        for f3 in range(noFields):
                            if f3!=f1 and f3!=f2 and f3!=middlefield:
                                if cse:
                                    staticfields.append(f3)
                                else:   # why reify? seems too strong.
                                    print "reify(eq(bState[%d, %d], bState[%d, %d]), moves[%d, %d])"%(step, f3, step+1, f3, step, mv)
                        
                        if cse:
                            print "reifyimply(sumgeq(["
                            print reduce(lambda a,b: str(a)+","+str(b), map(lambda f3: "equal[%d, %d]"%(step, f3), staticfields))
                            print "], %d), moves[%d, %d])"%(len(staticfields), step, mv)
    
    # STARTING STATE -- specified from 1.. whereas arrays indexed from 0 in minion
    for f1 in range(noFields):
        if f1!=startField-1:
            print "eq(bState[0, %d], 1)"%f1
    
    print "eq(bState[0, %d], 0)"%(startField-1)
    
    
    # Finishing state has one piece
    # (and all intermediate states have a fixed number)
    
    for step in range(noSteps+1):
        print "sumgeq([bState[%d, _]], %d)" % (step, noFields-step-1)
        print "sumleq([bState[%d, _]], %d)" % (step, noFields-step-1)
    
    print "**EOF**"


printpegsol(startPos)


