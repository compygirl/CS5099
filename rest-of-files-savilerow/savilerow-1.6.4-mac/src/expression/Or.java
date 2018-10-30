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
import java.util.*;
import java.io.*;
import savilerow.model.SymbolTable;
import savilerow.model.Sat;

public class Or extends ASTNode
{
    public static final long serialVersionUID = 1L;
	public Or(ArrayList<ASTNode> ch) {
		super(ch);
	}
	
	public Or(ASTNode l, ASTNode r) {
	    super(l, r);
	}
	
	public ASTNode copy()
	{
	    return new Or(getChildren());
	}
	public boolean isRelation(){return true;}
	
	public ASTNode simplify()
	{
	    
        boolean changed=false;
        
        ArrayList<ASTNode> ch=getChildren();
        for(int i=0; i<ch.size(); i++)
        {
            if(ch.get(i) instanceof Or) {
                changed=true;
                ASTNode curnode=ch.remove(i);
                i--;  // current element removed so move back in list.
                // Add children to end of this list, so that the loop will process them.
                ch.addAll(curnode.getChildren());
            }
        }
        
        // Constant folding
        for(int i=0; i<ch.size(); i++)
        {
            if(ch.get(i).isConstant())
            {
                long val=ch.get(i).getValue();
                if(val==1)
                {   // Found a true in the disjunction
                    return new BooleanConstant(true);
                }
                else
                {
                    changed=true;
                    ch.remove(i);
                    i--;
                }
            }
        }
        
        // Remove duplicates
        HashSet<ASTNode> a=new HashSet<ASTNode>(ch);
        if(a.size() < ch.size()) {
            changed=true;
            ch.clear();
            ch.addAll(a);
        }
        
        if(ch.size()==0) return new BooleanConstant(false); 
        if(ch.size()==1) return ch.get(0);
        if(changed) {
            return new Or(ch);
        }
        return this;
	}
	
	//  If contained in a Negate, push the negation inside using De Morgens law. 
	@Override
	public boolean isNegatable() {
	    return true;
	}
	@Override
	public ASTNode negation() {
	    ArrayList<ASTNode> newchildren=new ArrayList<ASTNode>();
	    
        for(int i=0; i<numChildren(); i++) {
            newchildren.add(new Negate(getChild(i)));
        }
        
        return new And(newchildren);
	}
	
	public boolean typecheck(SymbolTable st) {
	    for(ASTNode child :  getChildren()) {
	        if(!child.typecheck(st))
	            return false;
	        if(!child.isRelation()) {
	            System.out.println("ERROR: 'Or' contains numerical expression:"+child);
	            return false;
	        }
	    }
	    return true;
	}
	
	public ASTNode normalise() {
	    // sort by hashcode 
        ArrayList<ASTNode> ch=getChildren();
        boolean changed=sortByHashcode(ch);
        
        if(changed) return new Or(ch);
        else return this;
    }
    
    public boolean isCommAssoc() {
        return true;
    }
    
	public void toMinion(StringBuffer b, boolean bool_context)
	{
	    assert bool_context;  // Parent must expect a constraint here. 
	    b.append("watched-or({");
	    for(int i=0; i<numChildren(); i++)
	    {
	        getChild(i).toMinion(b, true);
	        if(i<numChildren()-1) b.append(",");
	    }
	    b.append("})");
	}
	
	public String toString() {
	    StringBuffer b=new StringBuffer();
	    b.append("(");
	    for(int i=0; i<numChildren(); i++) {
	        b.append(getChild(i).toString());
	        if(i<numChildren()-1) b.append(" \\/ ");
	    }
	    b.append(")");
	    return b.toString();
	}
	public void toDominionInner(StringBuffer b, boolean bool_context) {
	    b.append(CmdFlags.getCtName()+" ");
	    b.append("or([");
        for(int i=0; i<numChildren(); i++) {
            getChild(i).toDominion(b, true);
            if(i<numChildren()-1) b.append(",");
        }
        b.append("])");
	}
	public void toDominionParam(StringBuffer b) {
	    b.append("Or([");
        for(int i=0; i<numChildren(); i++) {
            getChild(i).toDominionParam(b);
            if(i<numChildren()-1) b.append(",");
        }
        b.append("])");
	}
	@Override
	public void toFlatzincWithAuxVar(StringBuffer b, ASTNode aux) {
	    b.append("constraint array_bool_or([");
	    for(int i=0; i<numChildren(); i++) {
            getChild(i).toFlatzinc(b, true);
            if(i<numChildren()-1) b.append(",");
        }
        b.append("],");
        aux.toFlatzinc(b, true);
        b.append(");");
	}
	public void toFlatzinc(StringBuffer b, boolean bool_context) {
	    b.append("constraint array_bool_or([");
	    for(int i=0; i<numChildren(); i++) {
            getChild(i).toFlatzinc(b, true);
            if(i<numChildren()-1) b.append(",");
        }
        b.append("],");
        b.append("true);");
	}
	
	public void toMinizinc(StringBuffer b, boolean bool_context) {
	    assert bool_context;
	    b.append("(");
        for(int i=0; i<numChildren(); i++) {
            getChild(i).toMinizinc(b, true);
            if(i<numChildren()-1) b.append(" \\/ ");
        }
        b.append(")");
    }
    
    public void toSAT(Sat satModel) throws IOException {
        ArrayList<Long> clause= new ArrayList<Long>();
        
        for (int i=0; i<numChildren(); i++) {
            ASTNode child=getChild(i);
            
            if(child instanceof Identifier || child instanceof SATLiteral) {
                clause.add(child.directEncode(satModel,1));
            }
            else {
                // This case allows other toSAT methods to make an Or and immediately encode it without
                // flattening it or simplifying. 
                long newsatvar = satModel.createAuxSATVariable();
                child.toSATWithAuxVar(satModel, newsatvar);
                clause.add(newsatvar);
            }
        }
        
        satModel.addClause(clause);
    }
    
    public void toSATWithAuxVar(Sat satModel, long auxVar) throws IOException {
        ArrayList<Long> clause=new ArrayList<Long>();
        
        for (ASTNode child : getChildren()) {
            if (child instanceof Identifier || child instanceof SATLiteral) {
                clause.add(child.directEncode(satModel,1));
            }
            else {
                long auxSatVar = satModel.createAuxSATVariable();
                child.toSATWithAuxVar(satModel, auxSatVar);
                clause.add(auxSatVar);
            }
        }
        
        satModel.addClauseReified(clause, auxVar);
    }
    
    @Override
	public boolean childrenAreSymmetric() {
	    return true;
	}
}