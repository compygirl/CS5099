/*
* Minion http://minion.sourceforge.net
* Copyright (C) 2006-09
*
* This program is free software; you can redistribute it and/or
* modify it under the terms of the GNU General Public License
* as published by the Free Software Foundation; either version 2
* of the License, or (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with this program; if not, write to the Free Software
* Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
*/

/** @help constraints;table Description
An extensional constraint that enforces GAC. The constraint is
specified via a list of tuples.

The variables used in the constraint have to be BOOL or DISCRETE variables.
Other types are not supported.
*/

/** @help constraints;table Example
To specify a constraint over 3 variables that allows assignments
(0,0,0), (1,0,0), (0,1,0) or (0,0,1) do the following.

1) Add a tuplelist to the **TUPLELIST** section, e.g.:

**TUPLELIST**
myext 4 3
0 0 0
1 0 0
0 1 0
0 0 1

N.B. the number 4 is the number of tuples in the constraint, the
number 3 is the -arity.

2) Add a table constraint to the **CONSTRAINTS** section, e.g.:

**CONSTRAINTS**
table(myvec, myext)

and now the variables of myvec will satisfy the constraint myext.
*/

/** @help constraints;table Example
The constraints extension can also be specified in the constraint
definition, e.g.:

table(myvec, {<0,0,0>,<1,0,0>,<0,1,0>,<0,0,1>})
*/

/** @help constraints;table References
help input tuplelist
help input gacschema
help input negativetable
help input haggisgac
*/

/** @help constraints;negativetable Description
An extensional constraint that enforces GAC. The constraint is
specified via a list of disallowed tuples.
*/

/** @help constraints;negativetable Notes
See entry

   help input negativetable

for how to specify a table constraint in minion input. The only
difference for negativetable is that the specified tuples are
disallowed.
*/

/** @help constraints;negativetable References
help input table
help input tuplelist
*/

#ifndef CONSTRAINT_GACTABLE_H_LAQ
#define CONSTRAINT_GACTABLE_H_LAQ

template<typename VarArray>
struct GACTableConstraint : public AbstractConstraint
{
  virtual string extended_name()
  { return "table(bound)"; }

  virtual string constraint_name()
  { return "table"; }

  CONSTRAINT_ARG_LIST2(vars, tuples);


  typedef typename VarArray::value_type VarRef;
  VarArray vars;
  TupleList* tuples;

#ifdef BINARY_SEARCH
  SysInt find_first_inconsistency(const vector<DomainInt>& v)
  {
    for(UnsignedSysInt i = 0; i < v.size(); ++i)
    {
      if(!vars[i].inDomain(v[i]))
        return i;
    }
    return -1;
  }

  void setFirstValid(vector<DomainInt>& support)
  {
    for(SysInt i = 0; i < (SysInt)support.size(); ++i)
      support[i] = vars[i].getMin();
  }

  SysInt setNextValid(vector<DomainInt>& support, SysInt var_considered, SysInt first_broken_val)
  {
    for(SysInt i = first_broken_val + 1; i < support.size(); ++i)
      support[i] = vars[i].getMin();

    for(SysInt i = first_broken_val; i >= 0; --i)
    {
      if(i != var_considered)
      {
        SysInt pos = std::max(support[i] + 1, vars[i].getMin());

        while(pos <= vars[i].getMax() && !vars[i].inDomain(pos))
          pos++;

        if(pos > vars[i].getMax())
          support[i] = vars[i].getMin();
        else
        {
          support[i] = pos;
          return i;
        }
      }
    }
    return -1;
  }
#endif

  LiteralSpecificLists* lists;

  void* _current_support;

  SysInt* current_support()
  { return (SysInt*)(_current_support); }

  /// Returns the tuple currently supporting a given literal.
  vector<DomainInt>& supporting_tuple(SysInt i)
  { return (lists->literal_specific_tuples)[i][current_support()[i]]; }

  /// Check if all allowed values in a given tuple are still in the domains of the variables.
  bool check_tuple(const vector<DomainInt>& v)
  {
    for(UnsignedSysInt i = 0; i < v.size(); ++i)
    {
      if(!vars[i].inDomain(v[i]))
        return false;
    }
    return true;
  }


  GACTableConstraint(const VarArray& _vars, TupleList* _tuples) :
    vars(_vars), tuples(_tuples), lists(_tuples->getLitLists())
  {
    CheckNotBound(vars, "table constraints","");
    if((SysInt)_vars.size() != lists->tuples->tuple_size())
    {
      FAIL_EXIT("In table constraint, number of variables is not equal to length of tuples.");
    }
    _current_support.request_bytes(lists->tuples->literal_num * sizeof(SysInt));
  }

  virtual SysInt dynamic_trigger_count()
  { return (lists->tuples->literal_num) * ( (SysInt)vars.size() - 1) ; }



  bool find_new_support(SysInt literal, SysInt var)
  {
    SysInt support = current_support()[literal];
    vector<vector<DomainInt> >& tuples = (lists->literal_specific_tuples)[literal];
    SysInt support_size = tuples.size();

    // These slightly nasty lines get us some nice raw pointers to the list of tuples.
    vector<DomainInt>* start_position = &*(tuples.begin());
    vector<DomainInt>* end_position = &*(tuples.begin()) + tuples.size();


#ifdef BINARY_SEARCH
    vector<DomainInt> new_support_tuple(vars.size());
    setFirstValid(new_support_tuple);
    while(true)
    {
      vector<DomainInt>* new_pos = lower_bound(start_position, end_position, new_support_tuple);
      if(new_pos == end_position)
        return false;

      SysInt problem_pos = find_first_inconsistency(*new_pos);

      if(problem_pos == -1)
      { // Found new support.
        current_support()[literal] = new_pos - start_position;
        return true;
      }

      new_support_tuple = *new_pos;
      if(setNextValid(new_support_tuple, var, problem_pos) == -1)
        return false;
    }
#else
    for(SysInt i = support; i < support_size; ++i)
    {
      if(check_tuple(tuples[i]))
      {
        current_support()[literal] = i;
        return true;
      }
    }

  for(SysInt i = 0; i < support; ++i)
    {
      if(check_tuple(tuples[i]))
      {
        current_support()[literal] = i;
        return true;
      }
    }

    return false;
#endif
  }

  virtual void propagate(DynamicTrigger* propagated_trig)
  {
    PROP_INFO_ADDONE(DynGACTable);

    DynamicTrigger* dt = dynamic_trigger_start();
    SysInt trigger_pos = propagated_trig - dt;
    SysInt propagated_literal = trigger_pos / ((SysInt)vars.size() - 1);

    pair<DomainInt, DomainInt> varval = (lists->tuples->get_varval_from_literal)(propagated_literal);
    BOOL is_new_support = find_new_support(propagated_literal, varval.first);
    if(is_new_support)
    {
      setup_watches(varval.first, varval.second);
                // better to just pass in varval.first and propagated_literal
                // setup_watches does not need value and recomputes lit
    }
    else
    {
      vars[varval.first].removeFromDomain(varval.second);
    }
  }

  void setup_watches(SysInt var, SysInt val)
  {
    SysInt lit = (lists->tuples->get_literal)(var, val);
    vector<DomainInt>& support = supporting_tuple(lit);

    DynamicTrigger* dt = dynamic_trigger_start();

    SysInt vars_size = vars.size();
    dt += lit * (vars_size - 1);
    for(SysInt v = 0; v < vars_size; ++v)
    {
      if(v != var)
      {
        vars[v].addDynamicTrigger(dt, DomainRemoval, support[v]);
        ++dt;
      }
    }
  }

  virtual void full_propagate()
  {
    for(UnsignedSysInt i = 0; i < vars.size(); ++i)
    {
      SysInt dom_min = (lists->tuples->dom_smallest)[i];
      SysInt dom_max = (lists->tuples->dom_smallest)[i] + (lists->tuples->dom_size)[i];
      vars[i].setMin(dom_min);
      vars[i].setMax(dom_max - 1);

      if(getState(stateObj).isFailed()) return;

      for(DomainInt x = vars[i].getMin(); x <= vars[i].getMax(); ++x)
      {
        SysInt literal = (lists->tuples->get_literal)(i, x);
        if((lists->literal_specific_tuples)[literal].empty())
        {
          vars[i].removeFromDomain(x);
        }
        else
        {
          current_support()[literal] = 0;
          BOOL is_new_support = find_new_support(literal, i);

          if(!is_new_support)
          {
            vars[i].removeFromDomain(x);
          }
          else
          { setup_watches(i, x); }
        }
      }
    }
  }

   virtual BOOL check_assignment(DomainInt* v, SysInt v_size)
  {
    for(SysInt i = 0; i < (lists->tuples)->size(); ++i)
    {
        if( std::equal(v, v + v_size, (*lists->tuples)[i]) )
        return true;
    }
    return false;
  }

    virtual vector<AnyVarRef> get_vars()
  {
    vector<AnyVarRef> anyvars;
    for(UnsignedSysInt i = 0; i < vars.size(); ++i)
      anyvars.push_back(vars[i]);
    return anyvars;
  }

};


template<typename VarArray>
AbstractConstraint*
GACTableCon(const VarArray& vars, TupleList* tuples)
{ return new GACTableConstraint<VarArray>(vars, tuples); }

#endif
