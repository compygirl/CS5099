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

public class NegativeTable extends ASTNode
{
    public static final long serialVersionUID = 1L;
    public NegativeTable(ASTNode v, ASTNode tups)
    {
        super(v,tups);
    }
    
	public ASTNode copy()
	{
	    return new NegativeTable(getChild(0), getChild(1));
	}
	public boolean isRelation(){return true;}
	
	public boolean typecheck(SymbolTable st) {
	    if(!getChild(0).typecheck(st)) return false;
	    if(!getChild(1).typecheck(st)) return false;
	    
	    if(getChild(0).getDimension()!=1) {
            CmdFlags.println("ERROR: First argument of negativetable should be 1-dimensional matrix: "+this);
            return false;
        }
	    if(getChild(1).getDimension()!=2) {
	        CmdFlags.println("ERROR: Second argument of negativetable should be 2-dimensional matrix: "+this);
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
                    // Constraint is always satisfied.
                    return new BooleanConstant(true);
                }
                
                if(getChild(0) instanceof EmptyMatrix) {
                    // ... and table is non-empty, i.e. contains one disallowed tuple of length 0. 
                    return new BooleanConstant(false);
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
                        ASTNode returntable=new NegativeTable(CompoundMatrix.makeCompoundMatrix(vars), CompoundMatrix.makeCompoundMatrix(newtab));
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
        return new Table(getChild(0), getChild(1));
    }
    
	public void toMinion(StringBuffer b, boolean bool_context)
	{
	    b.append("negativetable(");
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
	    else {
	        getChild(1).toMinion(b, false);
	    }
	    b.append(")");
	}
	public void toDominionInner(StringBuffer b, boolean bool_context)
	{
	    b.append(CmdFlags.getCtName()+" ");
	    b.append("negativetable(flatten(");
	    getChild(0).toDominion(b, false);
	    b.append("), ");
	    getChild(1).toDominion(b, false);
	    b.append(")");
	}
	
	public void toFlatzinc(StringBuffer b, boolean bool_context) {
	    b.append("constraint table_int_reif(");
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
        
        b.append("], false);");
	}
	
	public void toMinizinc(StringBuffer b, boolean bool_context) {
	    b.append("(not table(");
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
	    b.append("))");
	}
	
	public void toSAT(Sat satModel) throws IOException {
        ASTNode vars = getChild(0);
        ASTNode tab=getChild(1);
        
        if (tab instanceof Identifier) {
            tab = ((Identifier) tab).global_symbols.getConstantMatrix(tab.toString());
        }
        
        // Direct encoding.
        
        for (int i =1; i < tab.numChildren(); i++) {
            ArrayList<Long> cl=new ArrayList<Long>();
            
            for(int j=1; j<tab.getChild(i).numChildren(); j++) {
                long lit=vars.getChild(j).directEncode(satModel, tab.getChild(i).getChild(j).getValue());
                cl.add(-lit);
            }
            satModel.addClause(cl);
        }
    }
    
    public void toSATWithAuxVar(Sat satModel, long reifyVar) throws IOException {
        ASTNode vars = getChild(0);
        ASTNode tab=getChild(1);
        
        if (tab instanceof Identifier) {
            tab = ((Identifier) tab).global_symbols.getConstantMatrix(tab.toString());
        }
        
        ArrayList<Long> newVars=new ArrayList<Long>();
        
        for (int i =1; i < tab.numChildren(); i++) {
            ArrayList<Long> cl=new ArrayList<Long>();
            
            for(int j=1; j<tab.getChild(i).numChildren(); j++) {
                long lit=vars.getChild(j).directEncode(satModel, tab.getChild(i).getChild(j).getValue());
                cl.add(-lit);   //  Positive literals, unlike the direct encoding above. 
            }
            
            Long auxVar = satModel.createAuxSATVariable();
            newVars.add(auxVar);
            
            satModel.addClauseReified(cl, -auxVar);
            //  Literals negative and auxVar also negative.
            // Hence if auxVar is 1 then all literals must be set. 
        }
        
        // If reifyVar is true, we need all the newvars to be 0.
        // else, we need one of the newvars to be 1
        satModel.addClauseReified(newVars, -reifyVar);
    }
}
