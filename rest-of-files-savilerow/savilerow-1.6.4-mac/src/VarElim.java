package savilerow;
/*

    Savile Row http://savilerow.cs.st-andrews.ac.uk/
    Copyright (C) 2014, 2015 Peter Nightingale
    
    This file is part of Savile Row.
    
    Savile Row is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.
    
    Savile Row is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.
    
    You should have received a copy of the GNU General Public License
    along with Savile Row.  If not, see <http://www.gnu.org/licenses/>.

*/

import savilerow.expression.*;
import savilerow.treetransformer.*;
import savilerow.eprimeparser.EPrimeReader;
import savilerow.model.*;
import savilerow.solver.*;

import java.util.* ;
import java.io.* ;
import java.lang.ref.*;

// Heuristically eliminate variables, replace with an Or. 

// Assumes all references to decision variables are simple identifiers: all matrix derefs,]
// matrix slices etc have been simplified away. 

// Assumes also that constraints are contained in a top-level And. 

// WARNING:  this changes number of solutions.  Cannot be used when finding
// all solutions or first n solutions. Only for opportunistically getting
// any solution. 

public class VarElim 
{
    // Map of where variables occur
    private HashMap<ASTNode, ArrayList<ASTNode>> exp;
    
    Model m;
    int increase;
    int scale;
    
    boolean nooverlap;   // Eliminate vars when there are no shared vars between disjuncts
    
    public VarElim(Model _m) {
        m=_m;
        increase=0;  // Allow no increase in size of model due to variable elimination.
        scale=1;
    }
    
    public VarElim(Model _m, int _increase) {
        m=_m;
        increase=_increase;
        scale=1;
        nooverlap=false;
    }
    
    public VarElim(Model _m, int _increase, int _scale) {
        m=_m;
        increase=_increase;
        scale=_scale;
        nooverlap=false;
    }
    
    public VarElim(Model _m, boolean _nooverlap) {
        m=_m;
        increase=1;
        scale=1;
        nooverlap=_nooverlap;
    }
    
    public void eliminateVariables() {
        // Don't eliminate the optimisation variable, if there is one. 
        ASTNode optvar=m.objective;
        if(optvar!=null) optvar=optvar.getChild(0);
        assert optvar==null || optvar instanceof Identifier;
        
        boolean changed=true;  // AC1-style algorithm -- keep going until no changes are made. 
        
        //while(changed) {     //  Do one pass!!
            changed=false;
            
            // Set up the hash table. 
            exp=new HashMap<ASTNode, ArrayList<ASTNode>>();
            populate_exp(m.constraints, exp);
            
            // Iterate through variables in symbol table. 
            
            categoryentry itr=m.global_symbols.getCategoryFirst();
variableloop:
            while(itr!=null)
            {
                if(itr.cat==ASTNode.Decision || itr.cat==ASTNode.Auxiliary) {
                    String name=itr.name;
                    
                    ASTNode id=new Identifier(name, m.global_symbols);
                    
                    if(optvar!=null && id.equals(optvar)) {
                        continue variableloop;
                    }
                    
                    ArrayList<ASTNode> ls=exp.get(id);
                    
                    // filter out any detached ones
                    if(ls!=null) {
                        for(int i=0; i<ls.size(); i++) {
                            if(ls.get(i).isDetached()) {
                                ls.remove(i);
                                i--;
                            }
                        }
                    }
                    
                    if(ls==null || ls.size()==0) {
                        // Suspicious -- why is this variable used nowhere?
                        System.out.println("WARNING: Variable "+name+" is not in the scope of any constraint.");
                        
                        Intpair p=id.getBounds();
                        assert p.upper>=p.lower;
                        
                        if(id.isRelation()) {
                            m.global_symbols.assignVariable(id, new BooleanConstant( (p.lower==0)?false:true ));
                        }
                        else {
                            m.global_symbols.assignVariable(id, new NumberConstant(p.lower));
                        }
                        // No need to simplify because the variable does not occur anywhere. 
                    }
                    else {
                        //  Decide whether to eliminate this one. 
                        // Get all constraints containing the identifier
                        
                        HashSet<ASTNode> cts=new HashSet<ASTNode>();
                        
                        for(int i=0; i<ls.size(); i++) {
                            // scan up to the top-level and.
                            ASTNode occ=ls.get(i);
                            
                            while(! occ.getParent().inTopAnd()) {
                                occ=occ.getParent();
                            }
                            
                            cts.add(occ);
                        }
                        
                        ASTNode conj=new And(new ArrayList<ASTNode>(cts));
                        
                        // Now the actual var elimination...
                        //System.out.println("In Constraints:"+conj);
                        
                        ArrayList<Long> domvals=m.global_symbols.getDomain(name).getValueSet();
                        
                        ArrayList<ASTNode> disjunctionList=new ArrayList<ASTNode>();
                        for(int i=0; i<domvals.size(); i++) {
                            
                            ReplaceASTNode r=new ReplaceASTNode(id, new NumberConstant(domvals.get(i)));
                            disjunctionList.add(r.transform(conj.copy()));
                        }
                        
                        ASTNode disjunction=new Or(disjunctionList);
                        
                        //System.out.println("Replacement constraints before simplify:"+disjunction);
                        
                        TransformSimplify ts=new TransformSimplify();
                        disjunction=ts.transform(disjunction);
                        
                        // weirdly, the simplifier might give back something with a parent,
                        // for example if it returns one part of the disjunction. 
                        disjunction.setParent(null);
                        
                        //System.out.println("Replacement constraints after simplify:"+disjunction);
                        
                        if(heuristic(disjunction, conj)) {
                            System.out.println("Nuking variable :"+name);
                            
                            // Dig out the old constraints and replace with 'true'
                            ASTNode lasttrue=null;
                            for(int i=0; i<ls.size(); i++) {
                                // scan up to the top-level and.
                                ASTNode occ=ls.get(i);
                                
                                while(! occ.getParent().inTopAnd()) {
                                    occ=occ.getParent();
                                }
                                
                                
                                ASTNode t=new BooleanConstant(true);
                                occ.getParent().setChild(occ.getChildNo(), t);
                                lasttrue=t;
                            }
                            
                            // Replace the last one of the 'true's with the new disjunction constraint.
                            populate_exp(disjunction, exp);
                            assert disjunction.getParent()==null;
                            lasttrue.getParent().setChild(lasttrue.getChildNo(), disjunction);
                            
                            
                            // Delete the old variable.  -- It is potentially given the wrong value here. 
                            Intpair p=id.getBounds();
                            assert p.upper>=p.lower;
                            
                            //m.global_symbols.assignVariable(id, new NoValue());
                            if(id.isRelation()) {
                                m.global_symbols.assignVariable(id, new BooleanConstant( (p.lower==0)?false:true ));
                            }
                            else {
                                m.global_symbols.assignVariable(id, new NumberConstant(p.lower));
                            }
                            
                            changed=true;
                            /// HACK BELOW.
                            //break variableloop;
                        }
                        
                    }
                }
                
                // Next variable.
                itr=itr.next;
            }
            
            if(changed) {
                // Simplify entire model before looping. 
                // This does variable deletion if -deletevars is on. 
                // Also gets rid of any mess left by variable elimination. 
                m.simplify();
            }
        //}   // While(changed) loop end. 
        
        
    }
    
    private boolean heuristic(ASTNode replacement, ASTNode old) {
        if(!nooverlap) {
            return replacement.treesize()<= (scale * old.treesize())+increase;
        }
        else {
            //  'replacement' is the disjunction. 
            if(! (replacement instanceof Or)) {
                // Somehow the disjunction has simplified away to a single expression. 
                // Go ahead with replacement. 
                return true;
            }
            else {
                // Pull apart the disjunction.
                ArrayList<ASTNode> bits=replacement.getChildren();
                
                // For each bit, what vars does it contain. 
                ArrayList<HashMap<ASTNode, ArrayList<ASTNode>>> contains_vars=new ArrayList<HashMap<ASTNode, ArrayList<ASTNode>>>();
                for(int i=0; i<bits.size(); i++) {
                    HashMap<ASTNode, ArrayList<ASTNode>> tmp=new HashMap<ASTNode, ArrayList<ASTNode>>();
                    populate_exp(bits.get(i), tmp);
                    contains_vars.add(tmp);
                }
                
                HashSet<ASTNode> collected_vars=new HashSet<ASTNode>(contains_vars.get(0).keySet());
                
                boolean common_vars=false;
                
                for(int i=1; i<bits.size(); i++) {
                    HashSet<ASTNode> copy_cv=new HashSet<ASTNode>(collected_vars);
                    //System.out.println("Collected vars so far: "+copy_cv);
                    copy_cv.retainAll(contains_vars.get(i).keySet());  // Intersect vars in bits[i] with all previous. 
                    
                    if(copy_cv.size()>0) {
                        common_vars=true;
                        break;
                    }
                    
                    collected_vars.addAll(contains_vars.get(i).keySet());
                }
                
                return !common_vars;  // If there are no vars common to more than one bits[i], return true.
            }
        }
    }
    
    
    private void populate_exp(ASTNode a, HashMap<ASTNode, ArrayList<ASTNode>> map) {
        if(a instanceof Identifier && a.getCategory()==ASTNode.Decision) {
            if(map.containsKey(a)) {
                map.get(a).add(a);
            }
            else {
                ArrayList<ASTNode> list=new ArrayList<ASTNode>();
                list.add(a);
                map.put(a, list);
            }
        }
        
        for(int i=0; i<a.numChildren(); i++) {
            populate_exp(a.getChild(i), map);
        }
    }
}


