#include "../minion.h"
/* Minion Constraint Solver
   http://minion.sourceforge.net
   
   For Licence Information see file LICENSE.txt 
*/

#include "../constraints/constraint_gadget.h"

#ifdef REENTER
template<typename Vars>
AbstractConstraint*
BuildCT_GADGET(StateObj* stateObj, const Vars& vars, ConstraintBlob& blob)
{ return gadgetCon(stateObj, vars, blob); }
#else
template<typename Vars>
AbstractConstraint*
BuildCT_GADGET(StateObj* stateObj, const Vars& vars, ConstraintBlob& blob)
{ INPUT_ERROR("This constraint requires REENTER support."); return NULL; }
#endif

BUILD_CT(CT_GADGET, 1)
