package savilerow.expression;
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

import savilerow.*;
import savilerow.model.SymbolTable;
import java.util.*;
import java.io.*;
import savilerow.model.Sat;

public class Table extends ASTNode
{
    public static final long serialVersionUID = 1L;
    public Table(ASTNode v, ASTNode tups) {
        super(v,tups);
    }
    
	public ASTNode copy()
	{
	    return new Table(getChild(0), getChild(1));
	}
	public boolean isRelation(){return true;}
	
	public boolean typecheck(SymbolTable st) {
	    if(!getChild(0).typecheck(st)) return false;
	    if(!getChild(1).typecheck(st)) return false;
	    
	    if(getChild(0).getDimension()!=1) {
            CmdFlags.println("ERROR: First argument of table should be 1-dimensional matrix: "+this);
            return false;
        }
	    if(getChild(1).getDimension()!=2) {
	        CmdFlags.println("ERROR: Second argument of table should be 2-dimensional matrix: "+this);
            return false;
	    }
	    
        return true;
    }
    
    public ASTNode simplify() {
        if(getChild(0) instanceof CompoundMatrix || getChild(0) instanceof EmptyMatrix) {
            ASTNode table=getChild(1);
            if(table instanceof Identifier) {
                table=((Identifier)table).global_symbols.getConstantMatrix(table.toString());
            }
            
            if(table instanceof CompoundMatrix || table instanceof EmptyMatrix) {
                // Both vars and table are matrix types we can work with. 
                if(table instanceof EmptyMatrix) {
                    // It's an empty vector of tuples, not a vector containing a single empty tuple. 
                    return new BooleanConstant(false);
                }
                
                if(getChild(0) instanceof EmptyMatrix) {
                    // ... and table is non-empty
                    return new BooleanConstant(true);
                }
                
                ArrayList<ASTNode> vars=getChild(0).getChildren();
                vars.remove(0);
                
                // Simple one -- just project out assigned variables. 
                for(int i=0; i<vars.size(); i++) {
                    if(vars.get(i).isConstant()) {
                        long val=vars.get(i).getValue();
                        vars.remove(i);
                        
                        // Filter the table.
                        ArrayList<ASTNode> newtab=new ArrayList<ASTNode>();
                        for(int j=1; j<table.numChildren(); j++) {
                            if(table.getChild(j).getChild(i+1).getValue() == val) {
                                ArrayList<ASTNode> tmp=table.getChild(j).getChildren();
                                tmp.remove(0);   /// technically this doesn't make the O() any worse...
                                tmp.remove(i);  // Get rid of the column i
                                
                                newtab.add(CompoundMatrix.makeCompoundMatrix(tmp));
                            }
                        }
                        ASTNode returntable=new Table(CompoundMatrix.makeCompoundMatrix(vars), CompoundMatrix.makeCompoundMatrix(newtab));
                        return returntable;
                    }
                }
            }
        }
        return this;
    }
    
    @Override
    public boolean isNegatable() {
        return true;
    }
    @Override
    public ASTNode negation() {
        return new NegativeTable(getChild(0), getChild(1));
    }
    
	public void toMinion(StringBuffer b, boolean bool_context)
	{
	    assert bool_context;
	    if(getChild(0).numChildren()<=3) {
	        b.append("lighttable(");
	    }
	    else {
	        b.append("table(");
	    }
	    getChild(0).toMinion(b, false);
	    b.append(", ");
	    if(getChild(1) instanceof CompoundMatrix || getChild(1) instanceof EmptyMatrix) {
	        ArrayList<ASTNode> tups=getChild(1).getChildren();
	        b.append("{");
            for(int i=1; i<tups.size(); i++)
            {
                ArrayList<ASTNode> elements=tups.get(i).getChildren();
                b.append("<");
                for(int j=1; j<elements.size(); j++)
                {
                    elements.get(j).toMinion(b, false);
                    if(j<elements.size()-1) b.append(", ");
                }
                b.append(">");
                
                if(i<tups.size()-1) b.append(", ");
            }
            b.append("}");
	    }
	    else
	        getChild(1).toMinion(b, false);
	    b.append(")");
	}
	public void toDominionInner(StringBuffer b, boolean bool_context)
	{
	    b.append(CmdFlags.getCtName()+" ");
	    b.append("table(flatten(");
	    getChild(0).toDominion(b, false);
	    b.append("), ");
	    getChild(1).toDominion(b, false);
	    b.append(")");
	}
	public void toFlatzinc(StringBuffer b, boolean bool_context) {
	    b.append("constraint table_int(");
	    getChild(0).toFlatzinc(b, false);
	    b.append(",[");
	    ASTNode cmat;
	    
	    if(getChild(1) instanceof CompoundMatrix || getChild(1) instanceof EmptyMatrix) {
	        cmat=getChild(1);
	    }
	    else {
	        cmat=((Identifier)getChild(1)).global_symbols.getConstantMatrix(getChild(1).toString());
	    }
	    for(int i=1; i<cmat.numChildren(); i++) {
            for(int j=1; j<cmat.getChild(i).numChildren(); j++) {
                cmat.getChild(i).getChild(j).toFlatzinc(b, false);
                b.append(",");
            }
        }
        
        b.append("]);");
	}
	public void toMinizinc(StringBuffer b, boolean bool_context) {
	    b.append("table(");
	    getChild(0).toMinizinc(b, false);
	    b.append(",");
	    if(getChild(1) instanceof CompoundMatrix || getChild(1) instanceof EmptyMatrix) {
	        // Print out very strange Minizinc 2d array format.
	        b.append("[");
	        for(int i=1; i<getChild(1).numChildren(); i++) {
	            ASTNode ch=getChild(1).getChild(i);
	            b.append("| ");
	            for(int j=1; j<ch.numChildren(); j++) {
	                ch.getChild(j).toMinizinc(b, false);
	                if(j< ch.numChildren()-1) b.append(",");
	            }
	        }
	        b.append("|]");
	    }
	    else {
	        getChild(1).toMinizinc(b, false);
	    }
	    b.append(")");
	}
	
	
	public void toSAT(Sat satModel) throws IOException {
	    //toSATHelper(satModel, 0, false);
	    toSATHelper2(satModel);
	}
	public void toSATWithAuxVar(Sat satModel, long auxVar) throws IOException {
	    toSATHelper(satModel, auxVar, true);
	}
	
	////////////////////////////////////////////////////////////////////////////
	//
	//   First encoding of Table. Each tuple is represented with a SAT variable
	//   that is true iff the tuple is assigned. Then we have a disjunction of 
	//   these new SAT variables. Allows reification. 
	
	public void toSATHelper(Sat satModel, long auxVar, boolean toSATWithAuxVar) throws IOException {
        ASTNode tab=getChild(1);
        
        if(tab instanceof Identifier) {
            tab = ((Identifier) getChild(1)).global_symbols.getConstantMatrix(getChild(1).toString());
        }
        
        ArrayList<Long> newSatVars = new ArrayList<Long>(tab.numChildren());
        
        for (int i=1; i < tab.numChildren(); i++) {
            ASTNode tuple = tab.getChild(i);
            
            // One sat variable for each tuple. 
            long auxSatVar = satModel.createAuxSATVariable();
            
            ArrayList<Long> iffclause=new ArrayList<Long>();
            
            for (int j =1; j < tuple.numChildren(); j++) {
                long value=tuple.getChild(j).getValue();
                long satLit=getChild(0).getChild(j).directEncode(satModel, value);
                
                satModel.addClause((-auxSatVar)+" "+satLit);
                iffclause.add(-satLit);
            }
            
            iffclause.add(auxSatVar);
            satModel.addClause(iffclause);
            
            newSatVars.add(auxSatVar);
        }
        
        if(toSATWithAuxVar) {
            // Ensure one of the tuples is assigned iff auxVar
            satModel.addClauseReified(newSatVars, auxVar);
        }
        else {
            // Always ensure one of the tuples is assigned.
            satModel.addClause(newSatVars);
        }
    }
    
    ////////////////////////////////////////////////////////////////////////////
    //
    //   Second encoding of Table.
    //   I believe this one enforces GAC. 
    
    public void toSATHelper2(Sat satModel) throws IOException {
        ASTNode tab=getChild(1);
        
        if(tab instanceof Identifier) {
            tab = ((Identifier) getChild(1)).global_symbols.getConstantMatrix(getChild(1).toString());
        }
        
        ArrayList<ASTNode> tups=tab.getChildren();   // Now can filter out invalid tuples.
        
        ArrayList<ASTNode> vardoms=new ArrayList<ASTNode>();
        for(int i=1; i<getChild(0).numChildren(); i++) {
            ASTNode var=getChild(0).getChild(i);
            if(var instanceof Identifier) {
                vardoms.add(((Identifier)var).getDomain());
            }
            else {
                vardoms.add(new BooleanDomain());
            }
        }
        
        // Filter tuples.
        ArrayList<Long> tupleSatVars = new ArrayList<Long>(tups.size());
        
        // Make a SAT variable for each tuple. 
        for(int i=1; i < tups.size(); i++) {
            boolean valid=true;
            for(int var=1; var<getChild(0).numChildren(); var++) {
                if(!vardoms.get(var-1).containsValue(tups.get(i).getChild(var).getValue())) {
                    valid=false;
                    break;
                }
            }
            
            if(!valid) {
                tups.set(i, tups.get(tups.size()-1));
                tups.remove(tups.size()-1);
                i--;
                continue;
            }
            
            tupleSatVars.add(satModel.createAuxSATVariable());
        }
        
        for(int var=1; var<getChild(0).numChildren(); var++) {
            ASTNode varast=getChild(0).getChild(var);
            
            ArrayList<Long> vals=vardoms.get(var-1).getValueSet();
            ArrayList<Intpair> vals_intervalset=vardoms.get(var-1).getIntervalSet();
            
            // For each value in vals, construct a list of all tuples (sat vars representing tuples) containing that value. 
            
            ArrayList<ArrayList<Long>> clauses=new ArrayList<ArrayList<Long>>(vals.size());
            
            for(int i=0; i<vals.size(); i++) {
                clauses.add(new ArrayList<Long>());
            }
            
            for(int tup=1; tup<tups.size(); tup++) {
                long valintup=tups.get(tup).getChild(var).getValue();
                
                // Find the value in the domain
                int childidx=-1;   /// out of bounds
                int cumulativeindex=0;
                for(int j=0; j<vals_intervalset.size(); j++) {
                    Intpair p=vals_intervalset.get(j);
                    if( valintup>=p.lower && valintup<=p.upper) {
                        childidx=(int) (valintup-p.lower+cumulativeindex);
                        break;
                    }
                    cumulativeindex+=p.upper-p.lower+1;
                }
                
                if(childidx==-1) {
                    // Not in domain. Current tuple is invalid.
                    satModel.addClause(String.valueOf(-tupleSatVars.get(tup-1)));
                }
                else {
                    // Add the SAT var for this tuple to one of the clauses.
                    assert vals.get(childidx)==valintup;
                    clauses.get(childidx).add(tupleSatVars.get(tup-1));
                }
                
                
            }
            
            //  Now post the clauses 
            for(int i=0; i<vals.size(); i++) {
                satModel.addClauseReified(clauses.get(i), varast.directEncode(satModel, vals.get(i)));
            }
            
        }
        
        satModel.addClause(tupleSatVars);   // One of the tuples must be assigned -- probably redundant but probably won't hurt.
    }
    
}
