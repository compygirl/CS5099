#!/usr/bin/pypy 

# take a list of unsatisfying tuples for a constraint, and create a tree listing
# the propagations that need to be done for that constraint.

# At each node in the tree, branch yes/no on the literal that splits the remaining
# set of unsat tuples most evenly.


HEURISTIC_ENTAIL = 1
HEURISTIC_ANTI_ENTAIL = 2
HEURISTIC_STATIC = 3
HEURISTIC_LARGEST_POS_FIRST = 4
HEURISTIC_SMALLEST_POS_FIRST = 5
HEURISTIC_SMALLEST_POS_AND_IN_FIRST = 6
HEURISTIC_RANDOM = 7
HEURISTIC_ENTAIL2 = 8

GlobalHeuristic = 1


# domains_in is the values that are certainly in the domains. 
# domains_poss is the values that are possibly in. i.e. haven't been tested yet.
import copy
import sys
import cProfile
import random
sys.path.append("../python-scscp")
from VarValToDomain import initialize_domain,get_lit,get_total_litcount,get_lit_mapping
from perms import GetGraphGens, GetGroupSize, GetMinimalImage, ValueTotalPerm, ValuePermSwapList, VariableTotalPerm, LiteralPermSwapList, InvPerm, MultPerm, PadPerm, VariablePermSwapList
nodenumcounter = 0

nodenumprint = 100
def getnodenum():
    global nodenumcounter, nodenumprint
    nodenumcounter+=1
    if(nodenumcounter >= nodenumprint):
        print Comment, " Count: ", nodenumcounter
        nodenumprint *= 2
    return nodenumcounter


Group = False
MinimalImages = {}
EmptyKey = 'EmptyKey'
EmptyNodes = {}
EnableSymDetection = False
matched = 0
matchedprint = 10

TreeNodes={}   # maps from identifier to tree node. 
CountTreeSize=True

def markEmpty(nodenum):
    global EmptyNodes
    EmptyNodes[nodenum] = True


def adddomain(indom, maybedom, nodenum):
    global MinimalImages
    gap_indom = []
    gap_maybedom = []

    for i in range(0, len(indom)):
        for j in indom[i]:
            gap_indom.append(get_lit(i,j))
        for j in maybedom[i]:
            gap_maybedom.append(get_lit(i,j))
    MinImage = GetMinimalImage(Group, [gap_indom, gap_maybedom])
    Perm = MinImage[0]
    Image = MinImage[1]
    Image = tuple(map(lambda x : tuple(x), Image))

    if MinimalImages.has_key(Image):
        #print(["!", nodenum, gap_indom, gap_maybedom], MinImage, 
        #        MinimalImages[Image][0], InvPerm(Perm), Perm, 
        #        MultPerm(MinimalImages[Image][1], InvPerm(Perm)) )
        global matched, matchedprint
        matched += 1
        if matched >= matchedprint:
            print Comment, " Matched: ", matchedprint
            matchedprint *= 2
        if EmptyNodes.has_key(MinimalImages[Image][0]):
                # Node was empty
                return EmptyKey
        return [ MinimalImages[Image][0], MultPerm(MinimalImages[Image][1], InvPerm(Perm)) ]
    else:
        #print([[nodenum, gap_indom, gap_maybedom], Image, nodenum, Perm])
        MinimalImages[Image] = [nodenum, Perm]
        return False

def increment_vector(con, maxvals):
    pos = len(con) - 1
    while pos >= 0:
        con[pos] = con[pos] + 1
        if con[pos] >= maxvals[pos]:
            con[pos] = 0
        else:
            return True
        pos = pos - 1
    return False

def fastcrossprod(domainmax):
    outlist = []
    basevec = [0] * len(domainmax)
    outlist.append(basevec[:])
    while increment_vector(basevec, domainmax):
        outlist.append(basevec[:])
    return outlist

def crossprod(domains, conslist, outlist):
    if domains==[]:
        outlist.append(conslist[:])
        return
    for i in domains[0]:
        ccopy=conslist[:]
        ccopy.append(i)
        crossprod(domains[1:], ccopy, outlist)
    return

def tuple_less(tup1, tup2):
    for i in range(len(tup1)):
        if tup1[i]<tup2[i]:
            return True
        elif tup1[i]>tup2[i]:
            return False
    return False # tup1==tup2.

def binary_search(tuples, x):
    lo=0
    hi=len(tuples)
    while lo < hi:
        mid = (lo+hi)//2
        midval = tuples[mid]
        if tuple_less(midval, x):
            lo = mid+1
        elif midval==x:
            return mid
        else:
            hi=mid
    return -1



def gac2001_prunings(domains):
    prunings=[]
    for var in xrange(len(domains)):
        for val in domains[var]:
            validx=gac2001_domains_init[var].index(val)
            idx=gac2001_indices[var][validx]
            # loop from idx to end to find support
            tuplist=gac2001_goods[var][validx]
            
            support=False
            
            for tupidx in xrange(idx, len(tuplist)):
                valid=True
                tup=tuplist[tupidx]
                for i in xrange(len(tup)):
                    if tup[i] not in domains[i]:
                        valid= False
                        break
                if valid:
                    # tupidx is a support
                    support=True
                    gac2001_indices[var][validx]=tupidx
                    break
            if support:
                continue
            
            for tupidx in xrange(0, idx):
                valid=True
                tup=tuplist[tupidx]
                for i in xrange(len(tup)):
                    if tup[i] not in domains[i]:
                        valid= False
                        break
                if valid:
                    # tupidx is a support
                    support=True
                    gac2001_indices[var][validx]=tupidx
                    break
            if not support:
                prunings.append((var,val))
    return prunings


checkties=False

checktreecutoff=False



calls_build_tree=0

def filter_nogoods(tups_in, domains_in, domains_poss):
    # Return a new list with only the valid tuples.
    tups_out=[]
    for t in tups_in:
        flag=True
        for i in xrange(len(t)):
            if (t[i] not in domains_poss[i]) and (t[i] not in domains_in[i]):
                flag=False
                break
        if flag:
            tups_out.append(t)
    return tups_out


def build_tree(ct_init, tree, domains_in, domains_poss, varvalorder, heuristic):
    # take a list of unsatisfying tuples within domains_poss & domains_in.
    # a tree node
    # a domain list
    # and make two new tree nodes ... calls itself recursively.
    # Returns false if it and all its children do not do any pruning.
    #print "Top of build_tree"
    global calls_build_tree, TreeNodes
    calls_build_tree+=1
   
   
    # Filter the tuple list -- remove any tuples that are not valid
    ct=filter_nogoods(ct_init, domains_in, domains_poss)
    
    # Shallow copy the domains. Any changes must replace a domain with a new one.
    domains_in=domains_in[:]
    domains_poss=domains_poss[:]
    
    
    if ct == []:
        # The constraint is implied
        return False  # no pruning.
    
    # Now done after pruning:
    # If only one possible value left, assume it is 'in', otherwise we would have failed already.
    #for var in xrange(len(domains_poss)):
    #    if len(domains_in[var])==0 and len(domains_poss[var])==1:
    #        domains_in[var]=domains_in[var]+[domains_poss[var][0]]
    #        domains_poss[var]=[]
    
    ##########################################################################
    #
    #  GAC
    #  Find the GAC prunings required at this node.
    
    whole_domain=[]
    for i in xrange(len(domains_in)):
        whole_domain.append(domains_in[i]+domains_poss[i])
        whole_domain[i].sort()
    
    #print "About to do GAC."
    #print "tuples:"+str(ct)
    #print "domains_poss:"+str(domains_poss)
    #print "domains_in:"+str(domains_in)
    #print "domains:"+str(whole_domain)
    #print "domains_out:"+str(domains_out)
    
    prun=gac2001_prunings(whole_domain)
    
    if len(prun)>0:
        tree['pruning']=prun[:]
        # Remove the pruned values from the active domains
        for (var,val) in prun:
            if val in domains_in[var]:
                domains_in[var]=domains_in[var][:]
                domains_in[var].remove(val)
            else:
                assert val in domains_poss[var]
                domains_poss[var]=domains_poss[var][:]
                domains_poss[var].remove(val)
    
    # check if a domain is empty
    for var in xrange(len(domains_in)):
        if len(domains_in[var])+len(domains_poss[var])==0:
            return True # we did do pruning here.
            # Could change this node from some pruning to a fail.
    
    # Domains have changed, so do this:
    # If only one possible value left, assume it is 'in', otherwise we would have failed already.
    for var in xrange(len(domains_poss)):
        if len(domains_in[var])==0 and len(domains_poss[var])==1:
            domains_in[var]=domains_in[var]+[domains_poss[var][0]]
            domains_poss[var]=[]
    
    # If we have complete domain knowledge, then return.
    if len(filter(lambda x:len(x)>0, domains_poss))==0:
        if len(prun)==0:
            return False
        else:
            return True
    
    # Filter the tuple list again after pruning -- remove any tuples that are not valid
    ct2=filter_nogoods(ct, domains_in, domains_poss)
    
    if ct2 == []:
        # The constraint is implied.
        assert len(prun)>0  # Should only reach here if tuples lost by pruning.
        return True
    
    if EnableSymDetection:
        perm = adddomain(domains_in, domains_poss, tree['nodelabel'])
        if perm == EmptyKey:
            #print('# Empty node found')
            if len(prun)==0:
                return False
            else:
                return True
        if perm != False:
            if CountTreeSize:
                gotonodenum=perm[0]
                assert TreeNodes.has_key(gotonodenum)
                gotonodelist=[TreeNodes[gotonodenum]]
                gotonodecounter=0
                while gotonodelist:
                    curnode=gotonodelist[0]
                    gotonodelist=gotonodelist[1:]
                    if curnode.has_key('pruning'):
                        gotonodecounter+=2+(2*len(curnode['pruning']))
                    if curnode.has_key('perm'):
                        gotonodecounter+=1   # the instruction
                        gotonodecounter+=get_total_litcount()
                    if curnode.has_key('goto'):
                        gotonodecounter+=2   # jump takes 2 integers
                        continue  # make sure it doesn't count anything below.
                    
                    if curnode.has_key('left') or curnode.has_key('right'):
                        # branch instruction
                        gotonodecounter+=4  # for the branch instruction itself.
                        if curnode.has_key('left'):
                            gotonodelist.append(curnode['left'])
                        else:
                            gotonodecounter+=1  # for the 'end' instruction used for the left branch. 
                        if curnode.has_key('right'):
                            gotonodelist.append(curnode['right'])
                        
                        continue
                    else:
                        # return-succeed instruction
                        gotonodecounter+=1
                    
                
                (junk1, junk2, vmlist)=vm_tree(TreeNodes[gotonodenum], {}, [], [])
                assert len(vmlist)==gotonodecounter
                
                # now do the test.
                if gotonodecounter> ( 1+get_total_litcount()+2 )*TreeCutoff :   # is tree larger than size of perm and goto (multiplied by parameter)?
                    tree['perm'] = perm[1]
                    tree['goto'] = perm[0]
                    return True
            else:
                tree['perm'] = perm[1]
                tree['goto'] = perm[0]
                return True

    # No need to find singleton domains here, and put them 'in', because ...?
    
    ########################################################################
    #
    #  Heuristic
    
    chosenvar=-1
    chosenval=0
    
    PassToSmallPosFirst=False
    # default ordering
    
    if GlobalHeuristic == HEURISTIC_STATIC:
        for (var, val) in varvalorder:
            if val in domains_poss[var]:
                chosenvar=var
                chosenval=val
                break
    # choose the var and val contained in the most remaining nogoods
    # I.e. if it's not in domain, it will eliminate the most nogoods, pushing towards impliedness.
    if GlobalHeuristic == HEURISTIC_ENTAIL or GlobalHeuristic == HEURISTIC_ENTAIL2 or GlobalHeuristic == HEURISTIC_ANTI_ENTAIL:
        if GlobalHeuristic == HEURISTIC_ENTAIL or GlobalHeuristic == HEURISTIC_ENTAIL2:
            numnogoods=-1
        else:
            numnogoods=10000000000
        ties=[]
        for (var, val) in varvalorder:
            if val in domains_poss[var]:
                #count=len(filter(lambda a: a[var]==val,  ct2))
                count=0
                for nogood in ct2:
                    if nogood[var]==val:
                        count+=1
                
                if ((GlobalHeuristic == HEURISTIC_ENTAIL and count>numnogoods) or
                   (GlobalHeuristic == HEURISTIC_ANTI_ENTAIL and count<numnogoods)):
                    numnogoods=count
                    chosenvar=var
                    chosenval=val
                    ties=[(var,val)]
                elif count==numnogoods:
                    ties.append((var, val))
                
                if GlobalHeuristic == HEURISTIC_ENTAIL2 and count>=numnogoods:
                    # check if another value that is 'in'  has a nogood
                    othervalin=False
                    for valcheck in domains_in[var]:
                        for nogood in ct2:
                            if nogood[var]==valcheck:
                                othervalin=True
                                break
                        if othervalin: break
                    if not othervalin:
                        if count>numnogoods:
                            numnogoods=count
                            chosenvar=var
                            chosenval=val
                        else:
                            ties.append((var, val))
        
        if checkties:
            if len(ties)>1:
                print "Ties"
                # randomize
                #(chosenvar, chosenval)=random.choice(ties)
                
            else:
                print "No tie"
                
        if chosenvar==-1:
            PassToSmallPosFirst=True
        
    if GlobalHeuristic == HEURISTIC_SMALLEST_POS_FIRST or PassToSmallPosFirst:
        domsize = 10000000
        for (var, val) in varvalorder:
            if val in domains_poss[var]:
                if len(domains_poss[var]) < domsize:
                    domsize = len(domains_poss[var])
                    chosenvar = var
                    chosenval = val
    if GlobalHeuristic == HEURISTIC_LARGEST_POS_FIRST:
        domsize = -1
        for (var, val) in varvalorder:
            if val in domains_poss[var]:
                if len(domains_poss[var]) > domsize:
                    domsize = len(domains_poss[var])
                    chosenvar = var
                    chosenval = val

    if GlobalHeuristic == HEURISTIC_SMALLEST_POS_AND_IN_FIRST:
        domsize = 10000000
        for (var, val) in varvalorder:
            if val in domains_poss[var]:
                if len(domains_poss[var]) + len(domains_in[var]) < domsize:
                    domsize = len(domains_poss[var]) + len(domains_in[var])
                    chosenvar = var
                    chosenval = val

    if GlobalHeuristic == HEURISTIC_RANDOM:
        choices = []
        for (var, val) in varvalorder:
            if val in domains_poss[var]:
                choices += [(var,val)]
        (chosenvar, chosenval) = random.choice(choices)
    if GlobalHeuristic not in range(1, 9):
        sys.exit("Invalid heuristic")



    if chosenvar==-1:
        # this case arises when the varvalorder does not contain all var val pairs/
        # This might be because the last variable is functionally dependent on the 
        # others.
        # Treated same as complete domain knowledge.
        assert False
        if len(prun)==0:
            return False
        else:
            return True
    
    ##########################################################################
    #  
    #  Branching
    
    #print "Chosen variable: %d" %chosenvar
    #print "Chosen value:%d"%chosenval
    
    
    domains_poss[chosenvar]=domains_poss[chosenvar][:]
    domains_poss[chosenvar].remove(chosenval)
    
    #dom_left_in=copy.deepcopy(domains_in)
    #dom_right_in=copy.deepcopy(domains_in)
    
    # In left tree, move the value from possible to in
    #dom_left_in[chosenvar].append(chosenval)
    
    tree['var']=chosenvar
    tree['val']=chosenval

    prun_left=False
    prun_right=False
    
    tree['left']=dict()
    tree['left']['nodelabel'] = getnodenum()
    TreeNodes[tree['left']['nodelabel']]=tree['left']
    
    # just for left branch
    domains_in[chosenvar].append(chosenval)
    
    prun_left=build_tree(ct2, tree['left'], domains_in, domains_poss, varvalorder, heuristic)
    if not prun_left:
        if checktreecutoff:
            print "deleting subtree of size: %d"%(tree_cost2(tree['left']))
        markEmpty(tree['left']['nodelabel'])
        del tree['left']
    
    domains_in[chosenvar].remove(chosenval)
    
    tree['right']=dict()
    tree['right']['nodelabel'] = getnodenum()
    TreeNodes[tree['right']['nodelabel']]=tree['right']
    
    prun_right=build_tree(ct2, tree['right'], domains_in, domains_poss, varvalorder, heuristic)
    if not prun_right:
        if checktreecutoff:
            print "deleting subtree of size: %d"%(tree_cost2(tree['right']))
        markEmpty(tree['right']['nodelabel'])
        del tree['right']
    
    if (not prun_left) and (not prun_right) and (not tree.has_key('pruning')):
        return False
    else:
        return True


def old_print_tree(tree, indent="    "):
    if tree.has_key('nodelabel'):
      print indent+'// Number:' + str(tree['nodelabel'])
    else:
      print indent+"// No number"
    if tree.has_key('pruning'):
        for p in tree['pruning']:
            print indent+"vars[%d].removeFromDomain(%d);"%(p[0], p[1])
    if tree.has_key('goto'):
        print indent+str(tree['goto'])
        print indent+str(tree['perm'])
        return
    if tree.has_key('left'):
        print indent+"if(vars[%d].inDomain(%d))"%(tree['var'], tree['val'])
        print indent+"{"
        old_print_tree(tree['left'], indent+"    ")
        print indent+"}"
        if tree.has_key('right'):
            print indent+"else"
            print indent+"{"
            old_print_tree(tree['right'], indent+"    ")
            print indent+"}"
    else:
        if tree.has_key('right'):
            print indent+"if(!vars[%d].inDomain(%d))"%(tree['var'], tree['val'])
            print indent+"{"
            old_print_tree(tree['right'], indent+"    ")
            print indent+"}"
    return

# dot output

def outputdot(tree):
    print "digraph test {"
    
    node2id={}
    id2node={}
    numbertree(tree, "0", node2id, id2node)
    dotout=[]
    treetodot(tree, dotout, node2id, id2node)
    
    print "\n".join(dotout)
    print "}"

# Give an arbitrary unique number to each node.
# returns highest number used.
def numbertree(tree, st, node2id, id2node):
    tree['id']=st
    node2id[tree['nodelabel']]=st
    id2node[st]=tree['nodelabel']
    if tree.has_key('left'):
        numbertree(tree['left'], st+"0", node2id, id2node)
    if tree.has_key('right'):
        numbertree(tree['right'], st+"1", node2id, id2node)

def treetodot(tree, dotout, node2id, id2node):
    nodenumber=tree['id']
    
    if(tree.has_key('goto')):
        dotout+=[ nodenumber+' [label="",shape=box];' ]
        dotout+=[ nodenumber+" -> "+str(node2id[tree['goto']])+' [style=dashed]' ]
    else:
        dotout+=[ nodenumber+' [label="",shape=circle,color=red];']
        
    if tree.has_key('left'):
        dotout+=[ nodenumber+" -> "+str(node2id[tree['left']['nodelabel']]) ]
        treetodot(tree['left'], dotout, node2id, id2node)
    if tree.has_key('right'):
        dotout+=[ nodenumber+" -> "+str(node2id[tree['right']['nodelabel']]) ]
        treetodot(tree['right'], dotout, node2id, id2node)
    

def vm_tree(tree, nodestarts, jumppoints, currentvm):
    nodestarts[tree['nodelabel']] = len(currentvm)
    if(tree.has_key('pruning')):
        prune_list = [-1001]
        for p in tree['pruning']:
            prune_list += p
        prune_list += [-1]
        currentvm += prune_list
    if(tree.has_key('perm')):
        currentvm += [-2000]
        assert(get_total_litcount() > 0)
        paddedperm = PadPerm(tree['perm'], get_total_litcount())
        paddedperm = [ i - 1 for i in paddedperm ]
        currentvm += paddedperm
    if(tree.has_key('goto')):
        currentvm += [-1100, tree['goto'] + 10000]
        jumppoints.append(len(currentvm) - 1)
        return [nodestarts, jumppoints, currentvm]
    
    if(tree.has_key('left') or tree.has_key('right')):
        currentvm += [-1010]
        currentvm += [tree['var'], tree['val']]
        
        right_val = -3
        if tree.has_key('right'):
            right_val = tree['right']['nodelabel'] + 10000
            jumppoints.append(len(currentvm))
        currentvm += [right_val]
        
        if tree.has_key('left'):
            ret = vm_tree(tree['left'], nodestarts, jumppoints, currentvm)
            nodestarts = ret[0]
            jumppoints = ret[1]
            currentvm = ret[2]
        else:
            # need an 'end execution' instruction
            currentvm += [-1000]
        
        if tree.has_key('right'):
            ret = vm_tree(tree['right'], nodestarts, jumppoints, currentvm)
            nodestarts = ret[0]
            jumppoints = ret[1]
            currentvm = ret[2]
        return [nodestarts, jumppoints, currentvm]
    else:
        # No children
        currentvm += [-1000]
        return [nodestarts, jumppoints, currentvm]


def vm_tree_code_begin(tree):
    print "int get_mapping_size() { return " + str(len(get_lit_mapping())) + "; }"

    print "vector<int> get_mapping_vector() {"
    print "  vector<int> v;"
    lits = get_lit_mapping()
    for [var,val] in lits:
        print "  v.push_back(" + str(var) + ");",
        print "  v.push_back(" + str(val) + ");"
    print "  return v; "
    print "}"

    print "virtual void full_propagate() "
    print "{"
    print " FULL_PROPAGATE_INIT "
    vm_tree_code(tree)
    print "}"

def vm_tree_code(tree):
    # nodestarts[tree['nodelabel']] = len(currentvm)

    print "Label" + str(tree['nodelabel']) + ":     (void)1;"
    print "PRINT_MACRO(" + str(tree['nodelabel']) + ");"
    if tree.has_key('pruning'):
        for p in tree['pruning']:
            print "permutedRemoveFromDomain(PERM_ARGS, %d,%d);"%(p[0], p[1])
    
    
    if(tree.has_key('perm')):
        print "{"
        print "const int new_perm[" + str(get_total_litcount()) + "] = {",
        paddedperm = PadPerm(tree['perm'], get_total_litcount())
        for i in paddedperm:
            print str(i-1) + ",",
        print "};"

        print "  state = applyPermutation(PERM_ARGS, new_perm);"
        print "}"
    

    if(tree.has_key('goto')):
        print "goto Label" + str(tree['goto']) + ";"
        return
    
    if(tree.has_key('left') or tree.has_key('right')):
        print "if(permutedInDomain(PERM_ARGS, %d,%d))"%(tree['var'], tree['val'])
        print "{",
        if tree.has_key('left'):
            print "goto Label" + str(tree['left']['nodelabel']) + ";",
        else:
            print "return;",
        print "}"
        print "else"
        print "{",
        if tree.has_key('right'):
            print "goto Label" + str(tree['right']['nodelabel']) + ";",
        else:
            print "return;",
        print "}"

        if tree.has_key('left'):
            vm_tree_code(tree['left'])

        if tree.has_key('right'):
            vm_tree_code(tree['right'])
    else:
        print "return;"


def tree_cost(tree):
    # measure the max depth for now.
    l=0
    r=0
    if tree.has_key('left'):
        l=tree_cost(tree['left'])
    
    if tree.has_key('right'):
        r=tree_cost(tree['right'])
    
    return max(l,r)+1

def tree_cost2(tree):
    # number of nodes.
    l=0
    r=0
    if tree.has_key('left'):
        l=tree_cost2(tree['left'])
    
    if tree.has_key('right'):
        r=tree_cost2(tree['right'])
    
    return l+r+1

def gen_all_perms(permlist, perm, objects):
    if len(objects)==0:
        permlist.append(perm)
        return
    else:
        for i in xrange(len(objects)):
            # append an object onto perm
            perm2=perm[:]
            perm2.append(objects[i])
            objects2=objects[:i]+objects[i+1:]
            gen_all_perms(permlist, perm2, objects2)

def generate_tree(table, domains_init, heuristic, tablepositive=False):
    global gac2001_goods, gac2001_indices, gac2001_domains_init, TreeNodes
    bestcost=1000000000
    besttree=[]
    
    permlist=[]
    
    alltups=[]
    crossprod(domains_init, [], alltups)
    
    gac2001_goods=[ [ [] for a in dom ] for dom in domains_init ]  # make a list for each ltieral. 
    
    if not tablepositive:
        # we were given a negative table so invert it for gac2001.
        ct_nogoods=table
        
        for t in alltups:
            if binary_search(ct_nogoods, t)==-1:
                for var in xrange(len(t)):
                    val = t[var]
                    validx=domains_init[var].index(val)
                    gac2001_goods[var][validx].append(t)
    
    else:
        # given a positive table. Invert it for ct_nogoods.
        ct_nogoods=[]
        for t in alltups:
            if binary_search(table, t)==-1:
                ct_nogoods.append(t)
        
        # Populate supports arrays for gac2001
        for t in table:
            for var in xrange(len(t)):
                val = t[var]
                validx=domains_init[var].index(val)
                gac2001_goods[var][validx].append(t)
        
    
    # counter for each domain element
    gac2001_indices=[ [0 for a in dom ] for dom in domains_init  ]
    
    gac2001_domains_init=copy.deepcopy(domains_init)
    
    varvals=[(a,b) for a in xrange(len(domains_init)) for b in domains_init[a] ]
    if heuristic:
        permlist.append(varvals)
    else:
        gen_all_perms(permlist, [], varvals)
    
    for perm in permlist:
        tree=dict()
        tree['nodelabel']=getnodenum()
        TreeNodes[tree['nodelabel']]=tree
        domains_in=[ [] for i in domains_init]
        domains=copy.deepcopy(domains_init)
        print Comment, " Call buildtree"
        global MinimalImages
        MinimalImages = {}
        build_tree(copy.deepcopy(ct_nogoods), tree, domains_in, domains, perm, len(permlist)==1)   # last arg is whether to use heuristic.
        cost=tree_cost2(tree)
        print Comment, " Tree cost: %d" % cost
        if cost<bestcost:
            bestcost=cost
            besttree=tree
            print Comment, " Better tree found, of size:%d"%bestcost
    
    return besttree



def vm_print_tree(t):
    ret = vm_tree(t, dict(), [], [])

    nodestarts = ret[0]
    jumppoints = ret[1]
    currentvm  = ret[2]

    #print(ret)
    for j in jumppoints:
        assert(currentvm[j] >= 10000)
        #print(j, currentvm[j])
        nodepos = -3
        assert(nodestarts.has_key(currentvm[j] - 10000))
        nodepos = nodestarts[currentvm[j] - 10000]
        currentvm[j] = nodepos
    print("**TUPLELIST**")
    print("con 1 " + str(len(currentvm)))
    for i in currentvm:
      print(str(i) + " "),
    print("")
    if EnableSymDetection:
        lits = get_lit_mapping()
        print("mapping 1 " + str(len(lits) * 2))
        for [var,val] in lits:
            print(str(var) + " " + str(val) + " ")
    else:
        print("mapping 1 0 ")



def choose_print_tree(t):
    if EnableVMOutput:
        print "MINION 3"
        vm_print_tree(t)
    else:
        print "#ifdef PREPARE"
        if EnableSymDetection:
            print "#define SYMMETRIC"
        print "#else"
        if EnableSymDetection:
            vm_tree_code_begin(t)
        else:
            print "virtual void full_propagate() {"
            old_print_tree(t)
            print "}"
        print '#endif'
    print ""
    print Comment, " Depth: "+str(tree_cost(t))
    print Comment, " Number of nodes: "+str(tree_cost2(t))
    print Comment, " Number of nodes explored by algorithm: "+str(calls_build_tree)
    if EnableVMOutput:
        print "**EOF**"
    if EnableDotOutput:
        outputdot(t)


################################################################################
#
#
#       Constraints
#
#




def sports_constraint():
    # Channelling constraint from sports scheduling 10
    # Two variables domain 1..10
    # One variable domain 1..45
    # First generate the positive table
    postable=[]
    counter=1
    for i in xrange(1, 11):
        for j in xrange(i+1,11):
            postable.append([i,j,counter])
            counter=counter+1
    
    ct_nogoods=[]
    
    for i in xrange(1, 11):
        for j in xrange(1,11):
            for k in xrange(1, 46):
                t=[i,j,k]
                if t not in postable:
                    ct_nogoods.append(t)
    
    domains_init=[ range(1,11), range(1,11), range(1,46)]
    
    t=generate_tree(ct_nogoods, domains_init, True)
       
    choose_print_tree(t)

def sumgeqthree():
    # sumgeq-3 on 5 bool vars.
    global Group

    domains_init=[[0,1],[0,1],[0,1], [0,1], [0,1]]

    initialize_domain(domains_init)

    Group = VariableTotalPerm([0,1,2,3,4])

    nogoods=[]
    for a in range(2):
        for b in range(2):
            for c in range(2):
                for d in range(2):
                    for e in range(2):
                        if sum([a,b,c,d,e])<4:
                            nogoods.append([a,b,c,d,e])
    
    choose_print_tree(generate_tree(nogoods, domains_init, True))


def BIBD():
    # sumgeq-3 on 5 bool vars.
    global domainsize, Group, litcount
    domainsize = 2
    litcount = 2*2*7
    Group = [VariablePermSwapList(14, 2, [1,0,3,2,5,4,7,6,9,8,11,10,13,12]), VariablePermSwapList(14, 2, [2,3,0,1]), VariablePermSwapList(14, 2, [2,3,4,5,6,7,8,9,10,11,12,13,0,1])]
    print Group
    nogoods=[]
    cross=[]
    crossprod([(0,1) for i in range(14)], [], cross)
    for l in cross:
        s = sum(l[:7])
        t = sum(l[7:])
        prod = 0
        for i in range(7):
            prod += l[i] * l[i+7]
        if s != 3 or t != 3 or prod != 1:
            nogoods.append(l)

    domains_init=[[0,1] for i in range(14)]
   

    choose_print_tree(generate_tree(nogoods, domains_init, True))



    
def sokoban():
    # x+y=z where y has values -n, -1, 1, n
    nogoods=[]
    varvalorder=[]
    n=2   # width/height of grid.
    
    for x in range(n*n):
        for y in [-n, -1, 1, n]:
            for z in range(n*n):
                if x+y != z:
                    nogoods.append([x,y,z])
    
    domains_init=[range(n*n),[-n, -1, 1, n], range(n*n)]
    
    t=generate_tree(nogoods, domains_init, True)
    choose_print_tree(t)
    
def still_life():
    table=[]
    
    cross=[]
    crossprod([(0,1) for i in range(9)], [], cross)
    
    table=[]
    for l in cross:
        s=sum(l[:8])
        if s>3 or s<2:
            if l[8]==1:
                table.append(l)
        elif s==3:
            if l[8]==0:
                table.append(l)
        else:
            assert s==2
    
    domains_init=[[0,1] for i in range(9)]
    t=generate_tree(table, domains_init, True)
    choose_print_tree(t)
 
def gcc():
    global domainsize
    global litcount
    global Group
    domainsize = 3
    litcount   = 3*12
    table=[]
    Group = VariablePerm(12, 3, [0,1,2,3,4,5,6,7,8,9,10,11])
    cross=[]
    crossprod([(0,1,2) for i in range(12)], [], cross)
    
    table=[]
    for l in cross:
        zerocount=sum(map(lambda x: x == 0, l))
        onecount=sum(map(lambda x: x == 1, l))
        twocount=sum(map(lambda x: x == 2, l))
        if zerocount != 4 or onecount != 4 or twocount != 4:
            table.append(l)

    domains_init=[[0,1,2] for i in range(12)]
    t=generate_tree(table, domains_init, True)
    choose_print_tree(t)


 


def summinmax():
    global domainsize, Group, litcount
    vars = 14
    Group = VariablePerm(vars, 2, range(vars))
    domainsize = 2
    litcount = 2*vars
    table=[]
    
    cross=[]
    crossprod([(0,1) for i in range(vars)], [], cross)
    
    table=[]
    for l in cross:
        s=sum(l)
        if s%2==0:
          table.append(l)
    
    domains_init=[[0,1] for i in range(vars)]
    t=generate_tree(table, domains_init, True)
    choose_print_tree(t)
  


################################################################################
# Tidied up ones:
#

def alldiff():
    size = 4
    global Group
    domains_init=[range(size) for i in range(size)]
    initialize_domain(domains_init)

    Group = VariableTotalPerm(range(size)) + ValueTotalPerm(range(size))
    
    table=[]
    cross=[]
    crossprod([range(size) for i in range(size)], [], cross)
    
    table=[]
    for l in cross:
        if l[0]==l[1] or l[0]==l[2] or l[1]==l[2] or l[0]==l[3]  or l[1]==l[3] or l[2]==l[3]: # or  l[0]==l[4] or l[1]==l[4] or l[2]==l[4] or l[3]==l[4]: #or l[0]==l[5] or l[1]==l[5] or l[2]==l[5] or l[3]==l[5] or l[4]==l[5]:
              table.append(l)
    t=generate_tree(table, domains_init, True)
    choose_print_tree(t)

def and_constraint():
    global Group
    domains_init=[[0,1]]*3
    
    initialize_domain(domains_init)
    
    Group = VariableTotalPerm([0,1])
    
    # A /\ B = not C
    ct_nogoods=[[0,0,0], [0,1,0], [1,0,0], [1,1,1]]
    
    t=generate_tree(ct_nogoods, domains_init, True)
    
    choose_print_tree(t)

def and_constraint3():
    global Group
    domains_init=[[0,1]]*4
    
    initialize_domain(domains_init)
    
    Group = VariableTotalPerm([0,1,2])
    
    # A /\ B /\ C = not D
    ct_nogoods=[[0,0,0,0], [0,0,1,0], [0,1,0,0], [0,1,1,0], [1,0,0,0], [1,0,1,0], [1,1,0,0], [1,1,1,1]]
    
    t=generate_tree(ct_nogoods, domains_init, True)
    
    choose_print_tree(t)

def pegsol():
    global Group
    domains_init=[[0,1]]*7
    
    initialize_domain(domains_init)
    
    Group = ( VariableTotalPerm([0,2,5]) + VariableTotalPerm([1,3,4])  
        + LiteralPermSwapList({(0,0): (1,1), (0,1):(1,0), (1,0):(0,1), (1,1):(0,0)}) )
    # constraint for peg solitaire.
    
    nogoods=[]
    
    # One combination satisfies the conjunction.
    nogoods.append([1,0,1,0,0,1,0])
    
    for a in range(2):
        for b in range(2):
            for c in range(2):
                for d in range(2):
                    for e in range(2):
                        for f in range(2):
                            if not (a==1 and b==0 and c==1 and d==0 and e==0 and f==1):
                                # not satisfies the conjunction
                                nogoods.append([a,b,c,d,e,f,1])
    
    t=generate_tree(nogoods, domains_init, True)
    choose_print_tree(t)

def labs_two():
    # Constraint for low autocorrelation binary sequences
    global Group
    domains_init=[[-1,1],[-1,1],[-1,1], [-1,1], [-2,0,2]]
    
    initialize_domain(domains_init)
    
    Group = ( VariableTotalPerm([0,1]) + VariableTotalPerm([2,3]) + VariablePermSwapList([2,3,0,1]) + 
        LiteralPermSwapList({(0,-1): (0,1), (0,1):(0,-1), (1,-1):(1,1), (1,1):(1,-1)}) + 
        LiteralPermSwapList({(2,-1): (2,1), (2,1):(2,-1), (3,-1):(3,1), (3,1):(3,-1)}) +
                    LiteralPermSwapList({(0,-1): (0,1), (0,1):(0,-1), (2,-1):(2,1), (2,1):(2,-1),
                                 (4,-2): (4,2), (4,2):(4,-2)}) )
      
    # + ValuePermSwapList({-2:2, -1:1, 0:0, 1:-1, 2:-2})
    
    twoprod=[]
    for a in [-1, 1]:
        for b in [-1,1]:
            for c in [-1, 1]:
                for d in [-1, 1]:
                    for e in [-2,0,2]:
                        if e!= (a*b)+(c*d):
                            twoprod.append([a,b,c,d, e])
    
    t=generate_tree(twoprod, domains_init, True)
    choose_print_tree(t)

def labs_three():
    # Constraint for low autocorrelation binary sequences
    global Group
    domains_init=[[-1,1],[-1,1],[-1,1], [-1,1], [-1, 1], [-1, 1], [-3,-1,1,3]]
    
    initialize_domain(domains_init)
    
    Group = ( VariableTotalPerm([0,1]) + VariableTotalPerm([2,3]) + VariableTotalPerm([4,5]) +
            VariablePermSwapList([2,3,0,1]) + VariablePermSwapList([0,1,4,5,2,3]) +
            LiteralPermSwapList({(0,-1): (0,1), (0,1):(0,-1), (1,-1):(1,1), (1,1):(1,-1)}) +
            LiteralPermSwapList({(2,-1): (2,1), (2,1):(2,-1), (3,-1):(3,1), (3,1):(3,-1)}) + 
            LiteralPermSwapList({(4,-1): (4,1), (4,1):(4,-1), (5,-1):(5,1), (5,1):(5,-1)}) +
            LiteralPermSwapList({(0,-1): (0,1), (0,1):(0,-1), (2,-1):(2,1), (2,1):(2,-1),
                                 (4,-1): (4,1), (4,1):(4,-1),
                                 (6,-3): (6,3), (6,-1):(6,1), (6,1):(6,-1), (6,3):(6,-3)}) )
 
    # + ValuePermSwapList({-3:3, -2:2, -1:1, 0:0, 1:-1, 2:-2, 3:-3})
    
    threeprod=[]
    for a in [-1, 1]:
        for b in [-1,1]:
            for c in [-1, 1]:
                for d in [-1, 1]:
                    for e in [-1, 1]:
                        for f in [-1, 1]:
                            for g in [-3, -1, 1, 3]:
                                if (a*b)+(c*d)+(e*f) != g:
                                    threeprod.append([a,b,c,d,e,f,g])
    
    t=generate_tree(threeprod, domains_init, True)
    choose_print_tree(t)

def labs_four():
    # Constraint for low autocorrelation binary sequences
    global Group
    domains_init=[[-1,1],[-1,1],[-1,1], [-1,1], [-1, 1], [-1, 1], [-1, 1], [-1, 1], [-4,-2,0,2,4]]
    
    initialize_domain(domains_init)
    
    Group = ( VariableTotalPerm([0,1]) + VariableTotalPerm([2,3]) + 
        VariableTotalPerm([4,5]) + VariableTotalPerm([6,7]) + 
        VariablePermSwapList([2,3,0,1]) + VariablePermSwapList([0,1,4,5,2,3]) + 
        VariablePermSwapList([0,1,2,3,6,7,4,5]) + 
        LiteralPermSwapList({(0,-1): (0,1), (0,1):(0,-1), (1,-1):(1,1), (1,1):(1,-1)})  +
                    LiteralPermSwapList({(0,-1): (0,1), (0,1):(0,-1), (2,-1):(2,1), (2,1):(2,-1),
                                 (4,-1): (4,1), (4,1):(4,-1),(6,-1): (6,1), (6,1):(6,-1),
                                 (8,-4): (8,4), (8,4):(8,-4), (8,2):(8,-2), (8,-2):(8,2)})
    )
  
    # + ValuePermSwapList({-3:3, -2:2, -1:1, 0:0, 1:-1, 2:-2, 3:-3})
    
    fourprod=[]
    for a in [-1, 1]:
        for b in [-1,1]:
            for c in [-1, 1]:
                for d in [-1, 1]:
                    for e in [-1, 1]:
                        for f in [-1, 1]:
                            for g in [-1,1]:
                                for h in [-1,1]:
                                    for x in [-4,-2,0,2,4]:
                                        if (a*b)+(c*d)+(e*f)+(g*h) != x:
                                            fourprod.append([a,b,c,d,e,f,g,h,x])
            
    t=generate_tree(fourprod, domains_init, True)
    choose_print_tree(t)

def labs_five():
    # Constraint for low autocorrelation binary sequences
    global Group
    size = 5
    domains_init= ([ [-1,1] ] * (size*2)) + [ [-5,-3,-1,1,3,5] ]

    initialize_domain(domains_init)
    
    Group = ( VariableTotalPerm([0,1]) + VariableTotalPerm([2,3]) + 
        VariableTotalPerm([4,5]) + VariableTotalPerm([6,7]) + 
        VariableTotalPerm([8,9]) +
        VariablePermSwapList([2,3,0,1]) + VariablePermSwapList([0,1,4,5,2,3]) + 
        VariablePermSwapList([0,1,2,3,6,7,4,5]) + 
        VariablePermSwapList([0,1,2,3,4,5,8,9,6,7]) + 
        LiteralPermSwapList({(0,-1): (0,1), (0,1):(0,-1), (1,-1):(1,1), (1,1):(1,-1)}) +
          LiteralPermSwapList({(0,-1): (0,1), (0,1):(0,-1), (2,-1):(2,1), (2,1):(2,-1),
                                 (4,-1): (4,1), (4,1):(4,-1),(6,-1): (6,1), (6,1):(6,-1),
                                 (8,-1): (8,1), (8,1):(8,-1),
                                 (10,-5):(10,5), (10,-3): (10,3), (10,-1):(10,1), 
                                 (10,1):(10,-1), (10,3):(10,-3), (10,5):(10,-5)})
          )
  
    # + ValuePermSwapList({-3:3, -2:2, -1:1, 0:0, 1:-1, 2:-2, 3:-3})
    
    fourprod=[]
    for a in [-1, 1]:
        for b in [-1,1]:
            for c in [-1, 1]:
                for d in [-1, 1]:
                    for e in [-1, 1]:
                        for f in [-1, 1]:
                            for g in [-1,1]:
                                for h in [-1,1]:
                                    for i in [-1,1]:
                                        for j in [-1,1]:
                                            for x in [-5,-3,-1,1,3,5]:
                                                if (a*b)+(c*d)+(e*f)+(g*h)+(i*j) != x:
                                                    fourprod.append([a,b,c,d,e,f,g,h,i,j,x])
            
    t=generate_tree(fourprod, domains_init, True)
    choose_print_tree(t)

def labs_six():
    # Constraint for low autocorrelation binary sequences
    global Group
    size = 6
    domains_init= ([ [-1,1] ] * (size*2)) + [ [-6,-4,-2,0,2,4,6] ]

    initialize_domain(domains_init)
    
    Group = ( VariableTotalPerm([0,1]) + VariableTotalPerm([2,3]) + 
        VariableTotalPerm([4,5]) + VariableTotalPerm([6,7]) + 
        VariableTotalPerm([8,9]) + VariableTotalPerm([10,11]) +
        VariablePermSwapList([2,3,0,1]) + VariablePermSwapList([0,1,4,5,2,3]) + 
        VariablePermSwapList([0,1,2,3,6,7,4,5]) + 
        VariablePermSwapList([0,1,2,3,4,5,8,9,6,7]) + 
        VariablePermSwapList([0,1,2,3,4,5,6,7,10,11,8,9]) + 
        LiteralPermSwapList({(0,-1): (0,1), (0,1):(0,-1), (1,-1):(1,1), (1,1):(1,-1)}) +
        LiteralPermSwapList({(0,-1): (0,1), (0,1):(0,-1), (2,-1):(2,1), (2,1):(2,-1),
                                 (4,-1): (4,1), (4,1):(4,-1),(6,-1): (6,1), (6,1):(6,-1),
                                 (8,-1): (8,1), (8,1):(8,-1), (10,1):(10,-1), (10,-1):(10,1),
                              (12,-6):(12,6), (12,6):(12,-6), (12,-4): (12,4), (12,4):(12,-4), (12,2):(12,-2), (12,-2):(12,2)})
              )
  
    # + ValuePermSwapList({-3:3, -2:2, -1:1, 0:0, 1:-1, 2:-2, 3:-3})
    
    fourprod=[]
    for a in [-1, 1]:
        for b in [-1,1]:
            for c in [-1, 1]:
                for d in [-1, 1]:
                    for e in [-1, 1]:
                        for f in [-1, 1]:
                            for g in [-1,1]:
                                for h in [-1,1]:
                                    for i in [-1,1]:
                                        for j in [-1,1]:
                                            for k in [-1,1]:
                                                for l in [-1,1]:
                                                    for x in [-6,-4,-2,0,2,4,6]:
                                                        if (a*b)+(c*d)+(e*f)+(g*h)+(i*j)+(k*l) != x:
                                                            fourprod.append([a,b,c,d,e,f,g,h,i,j,k,l,x])
            
    t=generate_tree(fourprod, domains_init, True)
    choose_print_tree(t)

def life3d():
    global Group
    varcount = 3*3*3+1
    domains_init=[[0,1]]*varcount
    initialize_domain(domains_init)
    
    
    table=[]
    Group = VariableTotalPerm(range(varcount-2))
    cross=[]
    crossprod([(0,1) for i in range(varcount - 2)], [], cross)  # this line doesn't work.
    
    table=[]
    for l in cross:
        s=sum(l)
        if s>=5 and s<=5:
            l.append(0)
            l.append(1)
            table.append(l)
        elif s >= 7 and s <= 7:
            l.append(1)
            l.append(0)
            table.append(l)
        elif l[-2] == l[-1]:
            dup = l[:]
            l.append(0)
            l.append(0)
            dup.append(1)
            dup.append(1)
            table.append(l)
            table.append(dup)    
    
    print Comment, ' Generated constraint'
    t=generate_tree(table, domains_init, True)
    choose_print_tree(t)

def life():
    global Group
    domains_init=[[0,1] for i in range(10)]
    initialize_domain(domains_init)
    table=[]
    Group = VariableTotalPerm([0,1,2,3,4,5,6,7])
    cross=[]
    crossprod([(0,1) for i in range(10)], [], cross)
    
    table=[]
    for l in cross:
        s=sum(l[:8])
        if s>3 or s<2:
            if l[9]==1:
                table.append(l)
        elif s==3:
            if l[9]==0:
                table.append(l)
        else:
            assert s==2
            if l[8]!=l[9]:
                table.append(l)
            
    
    
    t=generate_tree(table, domains_init, True)
    choose_print_tree(t)

def lifeBriansBrain():
    global Group
    domains_init=[[0,1,2]]*10
    initialize_domain(domains_init)
    
    Group = VariableTotalPerm([0,1,2,3,4,5,6,7])
    cross = fastcrossprod([3]*10)
    
    table=[]
    # 0 = dead, 1 = alive, 2 = dying
    for l in cross:
        s=sum(map(lambda x: x == 1, l[:8]))    # used to be !=0
        if l[-2] == 2 and l[-1] != 0:
            table.append(l)
        elif l[-2] == 1 and l[-1] != 2:
            table.append(l)
        elif l[-2] == 0 and l[-1] == 1 and s != 2: # if s!=2 you cannot go from dead to alive.
            table.append(l)
        elif l[-2] == 0 and l[-1] == 0 and s == 2:
            table.append(l)
        elif l[-2] == 0 and l[-1] == 2:
            table.append(l)
            
    t=generate_tree(table, domains_init, True)
    choose_print_tree(t)


def lifeImmigration():
    global Group
    domains_init=[[0,1,2]]*10
    initialize_domain(domains_init)
    Group = VariableTotalPerm([0,1,2,3,4,5,6,7]) + ValueTotalPerm([1,2])  # can swap values 1 and 2.
    cross=[]
    crossprod(domains_init, [], cross)
    
    table=[]
    for l in cross:
        s=sum(map(lambda x: x != 0, l[:8]))
        onecount=sum(map(lambda x: x == 1, l[:8]))
        twocount=sum(map(lambda x: x == 2, l[:8]))
        if onecount > twocount:
            maxcol = 1
        else:
            maxcol = 2
        
        if s>3 or s<2: # should die, or stay dead
            if l[9]==1 or l[9]==2:
                table.append(l)
        elif l[8]==0: # might come to life, or stay dead
            if (s==2 and l[9]!=0) or (s==3 and l[9]!=maxcol):
                table.append(l)
        else: # cell alive, 2 or 3 neighbours, stays alive
            if l[8] != l[9]:
                table.append(l)
    
    t=generate_tree(table, domains_init, True)
    choose_print_tree(t)

def readTable():
    # This one reads the table from standard input. 
    global Group
    
    c=sys.stdin.readlines();
    c=" ".join(c)
    c=c.strip()
    
    c=eval(c)
    
    domains_init=c['doms']
    table=c['table']
    tabletype=c['type']

    initialize_domain(domains_init);
    
    if tabletype=='pos':
        tablepositive=True
    elif tabletype=='neg':
        tablepositive=False
    else:
        print("// Table type should be pos or neg.")
        sys.exit(-1)
    
    # Do magic Chris stuff here to get the symmetry group.
    
    
    if EnableSymDetection:
        maptables=[]
        for t in table:
            newt = []
            for i in range(len(t)):
                newt.append(get_lit(i,t[i]))
            maptables.append(newt)
        Group = GetGraphGens(maptables, get_total_litcount())
    t=generate_tree(table, domains_init, True, tablepositive)
    choose_print_tree(t)
    
    


# A tree node is a dictionary containing 'var': 0,1,2.... 'val', 'left', 'right', 'pruning'

# get rid of treenodes when there are no nogoods left.

#cProfile.run('life()')

if len(sys.argv)==5:
    TreeCutoff=float(sys.argv[4])
    #print "# read TreeCutoff value of %f"%TreeCutoff
else:
    TreeCutoff=1

if len(sys.argv) == 6:
    if sys.argv[4] != 'H':
        TreeCutoff=float(sys.argv[4])
    else:
        TreeCutoff=1
    GlobalHeuristic = int(sys.argv[5])

EnableVMOutput = eval(sys.argv[2])
EnableSymDetection = eval(sys.argv[3])
EnableDotOutput= False

if EnableVMOutput:
    Comment = '#'
else:
    Comment = '//'

eval(sys.argv[1]+"()")

if EnableSymDetection:
    print(Comment + "Group Size: " + str(GetGroupSize(Group)))
