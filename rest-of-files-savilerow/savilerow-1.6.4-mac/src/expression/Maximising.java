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


import java.util.*;
import savilerow.model.SymbolTable;
import savilerow.CmdFlags;

public class Maximising extends ASTNode
{
    public static final long serialVersionUID = 1L;
    public Maximising(ASTNode r)
    {
        super(r);
    }
    
    public ASTNode copy()
    {
        assert numChildren()==1;
        return new Maximising(getChild(0));
    }
    
    @Override
    public boolean typecheck(SymbolTable st) {
        if(!getChild(0).typecheck(st)) return false;
        if(getChild(0).getDimension()>0) {
            CmdFlags.println("ERROR: Expected numerical expression in maximising, found a matrix: "+this);
	        return false;
        }
        return true;
    }
    
    public void toMinion(StringBuffer b, boolean bool_context) {
        b.append("MAXIMISING ");
        getChild(0).toMinion(b, false);
        b.append("\n");
    }
    public String toString() {
        return "maximising "+getChild(0)+"\n";
    }
    public void toDominionInner(StringBuffer b, boolean bool_context) {
        b.append("maximising ");
        getChild(0).toDominion(b, false);
        b.append("\n");
    }
    public void toFlatzinc(StringBuffer b, boolean bool_context) {
        if(!getChild(0).isConstant()) {
            b.append("maximize ");
            getChild(0).toFlatzinc(b, false);
            b.append(";\n");
        }
        else {
            b.append("satisfy;\n");
        }
    }
    public void toMinizinc(StringBuffer b, boolean bool_context) {
        b.append("maximize ");
        getChild(0).toMinizinc(b, false);
        b.append(";\n");
    }
}
