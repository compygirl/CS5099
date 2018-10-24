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

#include "../BuildVariables.h"
#include "../inputfile_parse/CSPSpec.h"

namespace BuildCon
{

/// General case in iteratively build constraints.
/// This isn't inline, as we don't want the compiler to waste time inlining it.
template<ConstraintType constraint, SysInt size>
struct BuildConObj
{
  template<typename ConData>
  static
  AbstractConstraint* build(StateObj* stateObj, const ConData& partial_build, ConstraintBlob& b, SysInt pos) DOM_NOINLINE;
};


template<ConstraintType constraint, SysInt size>
template<typename ConData>
AbstractConstraint*
BuildConObj<constraint, size>::
build(StateObj* stateObj, const ConData& partial_build, ConstraintBlob& b, SysInt pos)
{
  const vector<Var>& vars = b.vars[pos];

  // type needs to be something for empty arrays
  SysInt type = VAR_CONSTANT;
  bool same_type = true;
  // Suppress unused warning
  (void)same_type;

  if(!vars.empty())
  {
    type = vars[0].type();
    for(UnsignedSysInt i = 1; i < vars.size(); ++i)
    {
        if(vars[i].type() != type)
        {
          same_type = false;
          break;
        }
    }
  }
  #ifndef QUICK_COMPILE
  if(same_type)
  {
    switch(type)
    {
      case VAR_BOOL:
      {
        vector<BoolVarRef> v(vars.size());
        for(UnsignedSysInt i = 0; i < vars.size(); ++i)
          v[i] = getVars(stateObj).boolVarContainer.get_var_num(vars[i].pos());
        return BuildConObj<constraint, size - 1>::
          build(stateObj, make_pair(partial_build, &v), b, pos + 1);
      }
      case VAR_NOTBOOL:
      {
        vector<VarNot<BoolVarRef> > v(vars.size());
        for(UnsignedSysInt i = 0; i < vars.size(); ++i)
          v[i] = VarNotRef(getVars(stateObj).boolVarContainer.get_var_num(vars[i].pos()));
        return BuildConObj<constraint, size - 1>::
          build(stateObj, make_pair(partial_build, &v), b, pos + 1);
      }
      case VAR_BOUND:
      {
        vector<BoundVarRef> v(vars.size());
        for(UnsignedSysInt i = 0; i < vars.size(); ++i)
          v[i] = getVars(stateObj).boundVarContainer.get_var_num(vars[i].pos());
        return BuildConObj<constraint, size - 1>::
          build(stateObj, make_pair(partial_build, &v), b, pos + 1);
      }
      case VAR_SPARSEBOUND:
      {
        vector<SparseBoundVarRef> v(vars.size());
        for(UnsignedSysInt i = 0; i < vars.size(); ++i)
          v[i] = getVars(stateObj).sparseBoundVarContainer.get_var_num(vars[i].pos());
        return BuildConObj<constraint, size - 1>::
          build(stateObj, make_pair(partial_build, &v), b, pos + 1);
      }
      case VAR_DISCRETE:
      {
        vector<BigRangeVarRef> v(vars.size());
        for(UnsignedSysInt i = 0; i < vars.size(); ++i)
          v[i] = getVars(stateObj).bigRangeVarContainer.get_var_num(vars[i].pos());
        return BuildConObj<constraint, size - 1>::
          build(stateObj, make_pair(partial_build, &v), b, pos + 1);
      }
      case VAR_SPARSEDISCRETE:
        INPUT_ERROR( "Sparse Discrete Variables current broken. Sorry");

      case VAR_CONSTANT:
      {
        vector<ConstantVar> v(vars.size());
        for(UnsignedSysInt i = 0; i < vars.size(); ++i)
          v[i] = ConstantVar(stateObj, vars[i].pos());
        return BuildConObj<constraint, size - 1>::
          build(stateObj, make_pair(partial_build, &v), b, pos + 1);
      }
    }
  }
  else
  #endif
  {
    vector<AnyVarRef> v(vars.size());
    for(UnsignedSysInt i = 0; i < vars.size(); ++i)
      v[i] = get_AnyVarRef_from_Var(stateObj, vars[i]);

    return BuildConObj<constraint, size - 1>::
      build(stateObj, make_pair(partial_build, &v), b, pos + 1);
  }
  // This FAIL_EXIT is here to stop a "no return in non-void function" warning. It should never be reached.
  INPUT_ERROR( "This should never be reached..");
}



}

AbstractConstraint*
build_constraint(StateObj* stateObj, ProbSpec::ConstraintBlob& b);
