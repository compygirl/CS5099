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

// To run before generic flattener. Preferably before any aux vars are introduced.
// Traverse expression tree and flatten identical expressions together, to the same aux variable.  

// Do longer expressions first

// Unfinished attempt to do CSE when the expression and quantifiers do not exactly match. 

public class CSEClass
{
    private HashMap<String, ArrayList<ASTNode>> exp;
    Model m;
    
    public void flattenCSEs(Model m_in) {
        m=m_in;
        exp=new HashMap<String, ArrayList<ASTNode>>();
        // Map from a string representation of an expression to a set of pointers to where it occurs in the tree.
        
        populate_exp(m.constraints);
        
        // Dump exp into an arraylist
        ArrayList<Map.Entry<String, ArrayList<ASTNode>>> exp2=new ArrayList<Map.Entry<String, ArrayList<ASTNode>>>(exp.entrySet());
        
        // Sort for longest string first.
        class cmpstrings implements Comparator<Map.Entry<String, ArrayList<ASTNode>>> {
            public int compare(Map.Entry<String, ArrayList<ASTNode>> x, Map.Entry<String, ArrayList<ASTNode>> y) {
                int sizex=x.getKey().length();
                int sizey=y.getKey().length();
                if(sizex>sizey) return -1;   // x<y
                if(sizex==sizey) return 0;   // x==y
                return 1;  // x>y
            }
        }
        cmpstrings cmpstrings1=new cmpstrings();
        Collections.sort(exp2, cmpstrings1);
        ArrayList<ASTNode> new_constraints=new ArrayList<ASTNode>();
        
        for(Map.Entry<String,ArrayList<ASTNode>> entry : exp2) {
            ArrayList<ASTNode> ls=entry.getValue();
            
            // Remove items from ls that are no longer connected to the root because of other CSEs being flattened
            for(int i=0; i<ls.size(); i++) {
                if(ls.get(i).isDetached()) {
                    ls.remove(i);
                    i--;
                }
            }
            
            if(ls.size()>1) {
                // Find out if any of them are top-level relations i.e. always true/false
                // COULD get rid of this if there was a rule to take top-level relation aux-vars and assign the var, propagate the consequences 
                // of assigning it through the model.
                boolean toplevel_defined=false;
                boolean toplevel_value=false;  // True or false, i.e. if an expression is found negated at the top level, the toplevel value is false.
                ASTNode toplevel_node=null;
                if(ls.get(0).isRelation()) {
                    for(ASTNode a : ls) {
                        if(a.getParent()==null || 
                            ((a.getParent() instanceof And) && a.getParent().getParent()==null)) {
                            toplevel_defined=true;
                            toplevel_value=true;
                            toplevel_node=a;
                        }
                    }
                }
                
                if(toplevel_defined) {
                    // Replace all non-top-level instances of this with toplevel_value
                    for(ASTNode a : ls) {
                        if(a!=toplevel_node) {
                            int childno=a.getParent().getChildren().indexOf(a);
                            ASTNode replacement=new BooleanConstant(toplevel_value);
                            a.getParent().setChild(childno, replacement);
                            replacement.setParent(a.getParent());
                        }
                    }
                }
                else {
                    // None of them are top-level relations. Do standard flattening.
                    Intpair bnd=ls.get(0).getBounds();
                    ASTNode auxvar=m.global_symbols.newAuxiliaryVariable(bnd.lower,bnd.upper);
                    ASTNode con=new ToVariable(ls.get(0), auxvar);
                    m.global_symbols.auxVarRepresentsConstraint( auxvar.toString(), "CSE: "+ls.size()+" occurrences of: "+(ls.get(0).toString()));
                    
                    for(ASTNode a : ls) {  // Replace all instances with the aux variable.
                        int childno=a.getParent().getChildren().indexOf(a);
                        a.getParent().setChild(childno, auxvar);   // copies auxvar and sets parent ptr of copy.
                    }
                    new_constraints.add(con);
                }
            }
        }
        
        // Conjoin all the new constraints onto the top level.
        new_constraints.add(m.constraints);
        m.constraints=new And(new_constraints);
    }
    
    private void populate_exp(ASTNode a)  {
        // If a is a potential CSE...
        // This should be a list of positive things rather than negative! e.g is it numerical or relational.
        if(a.toCSE()) { // Don't pick up sets or other unusual things
            ASTNode subs=a.copy();
            subs.setParent(a.getParent());
            substituteMarker(subs);
            
            String nam=subs.toString();
            if(exp.containsKey(nam)) {
                exp.get(nam).add(a);
            }
            else {
                ArrayList<ASTNode> list=new ArrayList<ASTNode>();
                list.add(a);
                exp.put(nam, list);
            }
        }
        
        for(int i=0; i<a.numChildren(); i++) {
            populate_exp(a.getChild(i));
        }
    }
    
    private void substituteMarker(ASTNode subs) {
        // Try to get rid of constant expressions containing quantifier variables, so x+j becomes x.
        // 
        // Retain isolated constants as much as possible. Don't want to match M[1,..] and M[2,..]
        // Retain decision variable expressions. Don't want to match M[x]=p and M[y]=p.
        
        ASTNode marker=new Identifier("__MARKER__", m.global_symbols);
        //m.global_symbols.newVariable("__MARKER__", new BooleanDomain(), ASTNode.Decision);
        
        System.out.println("Before substituting 0/1:"+subs);
        
        ArrayDeque<ASTNode> l=new ArrayDeque<ASTNode>();
        l.add(subs);
        // Breadth-first exploration.
        while(l.size()>0) {
            ASTNode curnode=l.remove();
            for(int i=0; i<curnode.numChildren(); i++) {
                if(curnode.getChild(i).getCategory()<=ASTNode.Quantifier && curnode.getChild(i).isNumerical()) {
                    if(curnode instanceof WeightedSum || curnode instanceof ShiftMapper) {
                        //System.out.println("Replacing:"+curnode.getChild(i));
                        curnode.setChild(i, new NumberConstant(0));
                    }
                    else if(curnode instanceof Times || curnode instanceof MultiplyMapper) {
                        //System.out.println("Replacing:"+curnode.getChild(i));
                        curnode.setChild(i, new NumberConstant(1));
                    }
                    else {
                        // default case.
                        curnode.setChild(i, marker);
                    }
                }
                else {
                    l.add(curnode.getChild(i));
                }
            }
        }
        
        subs=subs.simplify();
        
        System.out.println("After substituting 0/1:"+subs);
    }
}


