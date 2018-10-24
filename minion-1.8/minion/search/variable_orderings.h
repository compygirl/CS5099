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

#ifndef VARIABLE_ORDERINGS_H
#define VARIABLE_ORDERINGS_H

#include <cfloat>
//#include "../system/system.h"
//#include "../memory_management/reversible_vals.h"


template<typename T>
DomainInt chooseVal(T& var, ValOrderEnum vo)
{
    switch(vo)
    {
        case VALORDER_ASCEND:
            return var.getMin();
        case VALORDER_DESCEND:
            return var.getMax();


        case VALORDER_RANDOM:
        {
            if(var.isBound())
            {
                switch(rand()%2)
                {
                    case 0: return var.getMin();
                    case 1: return var.getMax();
                    default: abort();
                }
            }
            DomainInt min_val = var.getMin();
            DomainInt max_val = var.getMax();
            DomainInt val = (rand() % (max_val - min_val + 1)) + min_val;
            D_ASSERT(val >= min_val);
            D_ASSERT(val <= max_val);
            if(var.inDomain(val))
                return val;
            switch(rand()%2)
            {
                case 0:
                {
                    val--;
                    while(!var.inDomain(val))
                        val--;
                    return val;
                }
                case 1:
                {
                    val++;
                    while(!var.inDomain(val))
                        val++;
                    return val;
                }
                default: abort();
            }

        }

        default:
            abort();
    }
}

struct VariableOrder
{
    vector<AnyVarRef> var_order;  // can assume this is anyvarref? May need to template

    VariableOrder(const vector<AnyVarRef>& _var_order) : var_order(_var_order)
    {
    }

    // returns a pair of variable index, domain value.
    // returning the variable index == -1 means no branch possible.
    virtual pair<SysInt, DomainInt> pickVarVal() = 0;

    virtual ~VariableOrder() {}
};

// Container for multiple variable orderings
struct MultiBranch : public VariableOrder
{
    vector<shared_ptr<VariableOrder> > vovector;
    Reversible<SysInt> pos;

    // need to patch up the returned variable index
    vector<DomainInt> variable_offset;

    MultiBranch(const vector<shared_ptr<VariableOrder> > _vovector,
		StateObj* _stateObj):
    VariableOrder(_vovector[0]->var_order), // It doesn't matter what var_order is set to
    vovector(_vovector),
    pos(_stateObj)
    {
        pos=0;
        variable_offset.resize(vovector.size());
        variable_offset[0]=0;
        for(SysInt i=1; i<(SysInt)vovector.size(); i++)
        {
            variable_offset[i]=variable_offset[i-1]+vovector[i-1]->var_order.size();
        }
    }

    pair<SysInt, DomainInt> pickVarVal()
    {
        SysInt pos2=pos;

        pair<SysInt, DomainInt> t=vovector[pos2]->pickVarVal();
        while(t.first==-1)
        {
            pos2++;
            if(pos2== (SysInt)vovector.size())
            {
                return make_pair(-1, 0);
            }

            t=vovector[pos2]->pickVarVal();
        }
        pos=pos2;
        t.first+=checked_cast<SysInt>(variable_offset[pos2]);
        return t;
    }
};

struct StaticBranch : public VariableOrder
{
    vector<ValOrderEnum> val_order;
    Reversible<SysInt> pos;

    StaticBranch(const vector<AnyVarRef>& _var_order, const vector<ValOrderEnum>& _val_order, StateObj* _stateObj) : VariableOrder(_var_order),
        val_order(_val_order), pos(_stateObj)
    {
        pos=0;
        D_ASSERT(var_order.size() == val_order.size());
    }

    pair<SysInt, DomainInt> pickVarVal()
    {
        SysInt v_size = var_order.size();

        while(pos < v_size && var_order[pos].isAssigned())
            pos=pos+1;

        if(pos == v_size)
            return make_pair(-1, 0);

        DomainInt val=chooseVal(var_order[pos], val_order[pos]);

        return make_pair(pos, val);
    }
};

struct StaticBranchLimited : public VariableOrder
{
    vector<ValOrderEnum> val_order;
    Reversible<SysInt> pos;
    unsigned int limit;

    StaticBranchLimited(const vector<AnyVarRef>& _var_order, const vector<ValOrderEnum>& _val_order, unsigned int _limit, StateObj* _stateObj) : VariableOrder(_var_order),
        val_order(_val_order), pos(_stateObj), limit(_limit)
    {
        pos=0;
        D_ASSERT(var_order.size() == val_order.size());
    }

    pair<SysInt, DomainInt> pickVarVal()
    {
        SysInt v_size = var_order.size();

        while(pos < v_size && var_order[pos].isAssigned())
            pos=pos+1;
        
        if(pos>=limit) return make_pair(-1, 0);   /// This is the difference to StaticBranch.
        
        if(pos == v_size)
            return make_pair(-1, 0);

        DomainInt val=chooseVal(var_order[pos], val_order[pos]);

        return make_pair(pos, val);
    }
};

struct SDFBranch : public VariableOrder
{
    vector<ValOrderEnum> val_order;

    SDFBranch(const vector<AnyVarRef>& _var_order, const vector<ValOrderEnum>& _val_order, StateObj* _stateObj) :
        VariableOrder(_var_order), val_order(_val_order)
    {
    }

    // THIS DOES NOT DO SDF -- just an approximation with the bounds.

    pair<SysInt, DomainInt> pickVarVal()
    {
        //cout << "In pickVarVal for SDF (approximation)" <<endl;
        SysInt length = var_order.size();
        SysInt smallest_dom = -1;
        DomainInt dom_size = DomainInt_Max;

        for(SysInt i = 0; i < length; ++i)
        {
            DomainInt maxval = var_order[i].getMax();
            DomainInt minval = var_order[i].getMin();

            if((maxval != minval) && ((maxval - minval) < dom_size) )
            {
                dom_size = maxval - minval;
                smallest_dom = i;
                if( maxval - minval == 1)
                { // Binary domain, must be smallest
                    break;
                }
            }
        }

        if(smallest_dom==-1)
        {   // all assigned
            return make_pair(-1, 0);
        }

        DomainInt val=chooseVal(var_order[smallest_dom], val_order[smallest_dom]);

        return make_pair(smallest_dom, val);
    }
};

struct SlowStaticBranch : public VariableOrder
{
    vector<ValOrderEnum> val_order;

    SlowStaticBranch(const vector<AnyVarRef>& _var_order, const vector<ValOrderEnum>& _val_order, StateObj* _stateObj) :
        VariableOrder(_var_order), val_order(_val_order)
    {
    }

    pair<SysInt, DomainInt> pickVarVal()
    {
        UnsignedSysInt v_size = var_order.size();
        UnsignedSysInt pos = 0;
        while(pos < v_size && var_order[pos].isAssigned())
            ++pos;

        if(pos == v_size)
            return make_pair(-1, 0);

        DomainInt val=chooseVal(var_order[pos], val_order[pos]);

        return make_pair(pos, val);
    }
};

#ifdef WDEG
//see Boosting Systematic Search by Weighting Constraints by Boussemart et al
struct WdegBranch : public VariableOrder
{
    vector<ValOrderEnum> val_order;

    WdegBranch(const vector<AnyVarRef>& _var_order, const vector<ValOrderEnum>& _val_order, StateObj* _stateObj) : VariableOrder(_var_order),
        val_order(_val_order)
    {
    }

    pair<SysInt, DomainInt> pickVarVal()
    {
    SysInt best = var_order.size(); //the variable with the best score so far (init to none)
    SysInt best_score = -1; //... and its score (all true scores are positive)
    size_t var_order_size = var_order.size();
    for(size_t i = 0; i < var_order_size; i++) { //we will find the score for each var
      //cout << "i=" << i << endl;
      //cout << "best=" << best << endl;
      //cout << "best_score=" << best_score << endl;
      AnyVarRef v = var_order[i];
      if(v.isAssigned()) {
        //cout << "assigned -- stop" << endl;
        continue;
      }
      SysInt base_wdeg = checked_cast<SysInt>(v.getBaseWdeg());
      //cout << "basewdeg=" << base_wdeg << endl;
      if(base_wdeg <= best_score) {
        //cout << "too low before deductions" << endl;
        continue; //stop if base score is too low before deductions
      }
      vector<AbstractConstraint*>* constrs = v.getConstraints();
      size_t constrs_size = constrs->size();
      for(size_t j = 0; j < constrs_size; j++) { //find constrs to be deducted from var wdeg
        AbstractConstraint* c = (*constrs)[j];
        //cout << "con wdeg=" << c->getWdeg() << endl;
        vector<AnyVarRef>* c_vars = c->get_vars_singleton();
        size_t c_vars_size = c_vars->size();
        SysInt uninst = 0;
        for(size_t k = 0; k < c_vars_size; k++)
          if(!(*c_vars)[k].isAssigned())
            if(++uninst > 1) { //when multiple unassigned we needn't deduct
              //cout << "don't deduct" << endl;
              break;
            }
        if(uninst <= 1) {
          D_ASSERT(uninst == 1);
          //cout << "deduct" << endl;
          base_wdeg -= c->getWdeg();
          if(base_wdeg <= best_score) {
            //cout << "too low during deductions" << endl;
            break;
          }
        }
      }
      //cout << "basewdeg=" << base_wdeg << endl;
      if(best_score < base_wdeg)
      {
        //cout << "replacing top score" << endl;
        best_score = base_wdeg;
        best = i;
      }
    }

    // new bit. pn
    if(best==var_order.size())
        return make_pair(-1, 0);

    DomainInt val = chooseVal(var_order[best], val_order[best]);
    return make_pair(best, val);
  }
};

struct DomOverWdegBranch : VariableOrder
{
    vector<ValOrderEnum> val_order;

    DomOverWdegBranch(const vector<AnyVarRef>& _var_order, const vector<ValOrderEnum>& _val_order, StateObj* _stateObj) : VariableOrder(_var_order),
        val_order(_val_order)
    {
    }

    pair<SysInt, DomainInt> pickVarVal()
    {
    //cout << "using domoverwdeg" << endl;
    SysInt best = var_order.size(); //the variable with the best score so far (init to none)
    float best_score = FLT_MAX; //... and its score (all true scores are positive)
    size_t var_order_size = var_order.size();
    bool anyUnassigned = false;
    for(size_t i = 0; i < var_order_size; i++) { //we will find the score for each var
      //cout << "i=" << i << endl;
      //cout << "best=" << best << endl;
      //cout << "best_score=" << best_score << endl;
      AnyVarRef v = var_order[i];
      if(v.isAssigned()) {
        //cout << "assigned -- stop" << endl;
        continue;
      } else if(!anyUnassigned) {
        //always use the first unassigned as a fallback in case later calculations don't find
        //any variables with finite score
        best = i;
        anyUnassigned = true;
      }
      const SysInt dom_size_approx = checked_cast<SysInt>(v.getMax() - v.getMin() + 1);
      DomainInt base_wdeg = v.getBaseWdeg();
      //cout << "basewdeg=" << base_wdeg << endl;
      if((float)dom_size_approx/checked_cast<SysInt>(base_wdeg) >= best_score) {
        //cout << "too high before deductions" << endl;
        continue; //stop if base score is too low before deductions
      }
      vector<AbstractConstraint*>* constrs = v.getConstraints();
      size_t constrs_size = constrs->size();
      for(size_t j = 0; j < constrs_size; j++) { //find constrs to be deducted from var wdeg
        AbstractConstraint* c = (*constrs)[j];
        //cout << "con wdeg=" << c->getWdeg() << endl;
        vector<AnyVarRef>* c_vars = c->get_vars_singleton();
        size_t c_vars_size = c_vars->size();
        SysInt uninst = 0;
        for(size_t k = 0; k < c_vars_size; k++)
          if(!(*c_vars)[k].isAssigned())
            if(++uninst > 1) { //when multiple unassigned we needn't deduct
              //cout << "don't deduct" << endl;
              break;
            }
        if(uninst <= 1) {
          D_ASSERT(uninst == 1);
          //cout << "deduct" << endl;
          base_wdeg -= c->getWdeg();
          if((float)dom_size_approx/checked_cast<SysInt>(base_wdeg) >= best_score) {
            //cout << "too high during deductions,base_wdeg=" << base_wdeg << endl;
            break;
          }
        }
      }
      //cout << "basewdeg=" << base_wdeg << endl;
      if(best_score > (float)dom_size_approx/checked_cast<SysInt>(base_wdeg))
      {
        //cout << "replacing top score" << endl;
        best_score = (float)dom_size_approx/checked_cast<SysInt>(base_wdeg);
        best = i;
      }
    }
    //cout << "dec=" << best << "@" << best_score << endl;
    D_ASSERT(!anyUnassigned || best != var_order_size);

    // new bit. pn
    if(best==var_order.size())
        return make_pair(-1, 0);

    DomainInt val = chooseVal(var_order[best], val_order[best]);
    return make_pair(best, val);
  }
};
#endif



struct SRFBranch : VariableOrder
{
    vector<ValOrderEnum> val_order;

    SRFBranch(const vector<AnyVarRef>& _var_order, const vector<ValOrderEnum>& _val_order, StateObj* _stateObj) : VariableOrder(_var_order),
        val_order(_val_order)
    {
    }

    pair<SysInt, DomainInt> pickVarVal()
    {
    SysInt length = var_order.size();
    SysInt smallest_dom = length;

    float ratio = 2;


    for(SysInt i = 0; i < length; ++i)
    {
      DomainInt maxval = var_order[i].getMax();
      DomainInt minval = var_order[i].getMin();

      DomainInt original_minval = var_order[i].getInitialMin();
      DomainInt original_maxval = var_order[i].getInitialMax();

      float new_ratio = (checked_cast<float>(maxval - minval) * 1.0) / checked_cast<float>(original_maxval - original_minval);
        if((maxval != minval) && (new_ratio < ratio) )
        {
            ratio = new_ratio;
            smallest_dom = i;
        }
    }

    if(smallest_dom==length)
        return make_pair(-1, 0);

    DomainInt val = chooseVal(var_order[smallest_dom], val_order[smallest_dom]);
    return make_pair(smallest_dom, val);
    }
};


struct LDFBranch : VariableOrder
{
    vector<ValOrderEnum> val_order;

    LDFBranch(const vector<AnyVarRef>& _var_order, const vector<ValOrderEnum>& _val_order, StateObj* _stateObj) : VariableOrder(_var_order),
        val_order(_val_order)
    {
    }

    pair<SysInt, DomainInt> pickVarVal()
    {
    SysInt length = var_order.size();

    SysInt pos = 0;
    while(pos < length && var_order[pos].isAssigned())
      ++pos;
    if(pos == length)
    {
        return make_pair(-1, 0);
    }

    SysInt largest_dom = pos;
    DomainInt dom_size = var_order[pos].getMax() - var_order[pos].getMin();

    ++pos;

    for(; pos < length; ++pos)
    {
      DomainInt maxval = var_order[pos].getMax();
      DomainInt minval = var_order[pos].getMin();

      if(maxval - minval > dom_size)
      {
        dom_size = maxval - minval;
        largest_dom = pos;
      }
    }

    DomainInt val = chooseVal(var_order[largest_dom], val_order[largest_dom]);
    return make_pair(largest_dom, val);
  }
};

struct ConflictBranch : VariableOrder
{
    // Implements the conflict variable ordering from
    // "Last Conflict based Reasoning", Lecoutre et al, ECAI 06.
    vector<ValOrderEnum> val_order;

    Reversible<SysInt> pos;

    VariableOrder* innervarorder;

    ConflictBranch(const vector<AnyVarRef>& _var_order, const vector<ValOrderEnum>& _val_order,
        VariableOrder* _innervarorder, StateObj* _stateObj) :
    VariableOrder(_var_order),
    val_order(_val_order),
    pos(_stateObj), innervarorder(_innervarorder), last_returned_var(-1), in_conflict(false)
    {
        pos=0;
        pos2=0;
    }

    // pos maintains a 'depth' which is actually the number of calls to pickVarVals
    // last_returned_var contains the last var returned by pickvarval, or -1
    // if we were at a solution.

    // pos and pos2 are used to see if we have backtracked.

    SysInt pos2;

    SysInt last_returned_var;
    bool in_conflict;

    pair<SysInt, DomainInt> pickVarVal()
    {
        if(in_conflict && var_order[last_returned_var].isAssigned())
        {
            // If the conflict variable has been successfully assigned, come out
            // of conflict mode.
            in_conflict=false;
        }

        if(pos2>pos)
        {
            pos2=pos;

            if(last_returned_var!=-1 && !var_order[last_returned_var].isAssigned())
            {
                // we backtracked since the last call.
                // Assume the search procedure made a left branch which failed,
                // then backtracked.
                // Go into conflict mode.
                in_conflict=true;
            }
        }

        pos=pos+1;
        pos2++;

        if(in_conflict)
        {

            DomainInt val = chooseVal(var_order[last_returned_var], val_order[last_returned_var]);
            return make_pair(last_returned_var, val);
        }
        else
        {
            pair<SysInt, DomainInt> temp= innervarorder->pickVarVal();
            last_returned_var=temp.first;
            D_ASSERT(temp.first==-1 || var_order[temp.first].inDomain(temp.second));
            return temp;
        }
    }

};

#endif
