import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { ArrowLeft, Loader2, Plus, RefreshCw, ShieldCheck, Trash2, Users, Wallet } from "lucide-react";
import { api, getUserInfo } from "@/lib/api";
import { AdminDashboardResponse, AdminPlanUpsertRequest, AdminUserResponse, PublicPlanResponse } from "@/lib/types";
import { useToast } from "@/hooks/use-toast";
import { cn } from "@/lib/utils";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Switch } from "@/components/ui/switch";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";

const DEFAULT_PLAN_FORM: AdminPlanUpsertRequest = {
  name: "",
  priceInPaise: 0,
  maxProjects: 1,
  maxTokensPerDay: 100,
  unlimitedAi: false,
  validityDays: 30,
  active: true,
};

export default function AdminPage() {
  const navigate = useNavigate();
  const { toast } = useToast();
  const userInfo = getUserInfo();

  const [dashboard, setDashboard] = useState<AdminDashboardResponse | null>(null);
  const [users, setUsers] = useState<AdminUserResponse[]>([]);
  const [plans, setPlans] = useState<PublicPlanResponse[]>([]);
  const [query, setQuery] = useState("");
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [planDialogOpen, setPlanDialogOpen] = useState(false);
  const [editingPlan, setEditingPlan] = useState<PublicPlanResponse | null>(null);
  const [planToDelete, setPlanToDelete] = useState<PublicPlanResponse | null>(null);
  const [planForm, setPlanForm] = useState<AdminPlanUpsertRequest>(DEFAULT_PLAN_FORM);

  const paidPlanCount = useMemo(
    () => plans.filter((plan) => (plan.priceInPaise ?? 0) > 0).length,
    [plans]
  );

  const load = async (searchQuery?: string) => {
    const [dashboardResponse, usersResponse, plansResponse] = await Promise.all([
      api.getAdminDashboard(),
      api.getAdminUsers(searchQuery),
      api.getAdminPlans(),
    ]);

    setDashboard(dashboardResponse);
    setUsers(usersResponse);
    setPlans(plansResponse);
  };

  useEffect(() => {
    if (!userInfo || userInfo.role !== "ADMIN") {
      navigate("/projects");
      return;
    }

    const bootstrap = async () => {
      try {
        await load();
      } catch (error) {
        toast({
          title: "Admin data load failed",
          description: error instanceof Error ? error.message : "Please try again.",
          variant: "destructive",
        });
      } finally {
        setLoading(false);
      }
    };

    bootstrap();
  }, [navigate, toast, userInfo]);

  const resetPlanEditor = () => {
    setEditingPlan(null);
    setPlanForm(DEFAULT_PLAN_FORM);
  };

  const openCreatePlanDialog = () => {
    resetPlanEditor();
    setPlanDialogOpen(true);
  };

  const openEditPlanDialog = (plan: PublicPlanResponse) => {
    setEditingPlan(plan);
    setPlanForm({
      name: plan.name ?? "",
      priceInPaise: plan.priceInPaise ?? 0,
      maxProjects: plan.maxProjects ?? 1,
      maxTokensPerDay: plan.maxTokensPerDay ?? 100,
      unlimitedAi: Boolean(plan.unlimitedAi),
      validityDays: plan.validityDays ?? 30,
      active: Boolean(plan.active),
    });
    setPlanDialogOpen(true);
  };

  const handleRefresh = async () => {
    setRefreshing(true);
    try {
      await load(query);
    } catch (error) {
      toast({
        title: "Refresh failed",
        description: error instanceof Error ? error.message : "Please try again.",
        variant: "destructive",
      });
    } finally {
      setRefreshing(false);
    }
  };

  const handlePlanSubmit = async () => {
    setSubmitting(true);
    try {
      if (editingPlan) {
        await api.updateAdminPlan(editingPlan.id, planForm);
        toast({ title: "Plan updated", description: `${planForm.name} updated successfully.` });
      } else {
        await api.createAdminPlan(planForm);
        toast({ title: "Plan created", description: `${planForm.name} is now available.` });
      }

      setPlanDialogOpen(false);
      resetPlanEditor();
      await load(query);
    } catch (error) {
      toast({
        title: editingPlan ? "Plan update failed" : "Plan creation failed",
        description: error instanceof Error ? error.message : "Please review the details and try again.",
        variant: "destructive",
      });
    } finally {
      setSubmitting(false);
    }
  };

  const formatPrice = (priceInPaise?: number) => {
    if (!priceInPaise) return "Free";
    return `Rs ${ (priceInPaise / 100).toLocaleString("en-IN") }`;
  };

  const handleSearch = async () => {
    try {
      await load(query);
    } catch (error) {
      toast({
        title: "Search failed",
        description: error instanceof Error ? error.message : "Please try again.",
        variant: "destructive",
      });
    }
  };

  if (!userInfo || userInfo.role !== "ADMIN") {
    return null;
  }

  return (
    <div className="min-h-screen bg-slate-950 text-slate-50">
      <div className="mx-auto flex max-w-7xl flex-col gap-8 px-4 py-8 sm:px-6 lg:px-8">
        <div className="rounded-3xl border border-slate-800 bg-gradient-to-br from-slate-900 via-slate-950 to-slate-900 p-6 shadow-2xl">
          <div className="flex flex-col gap-6 lg:flex-row lg:items-center lg:justify-between">
            <div className="space-y-3">
              <Badge className="bg-emerald-500/15 text-emerald-300 hover:bg-emerald-500/15">
                <ShieldCheck className="mr-1 h-3.5 w-3.5" />
                Admin workspace
              </Badge>
              <div>
                <h1 className="text-3xl font-semibold tracking-tight">Control Center</h1>
                <p className="mt-2 max-w-2xl text-sm text-slate-300">
                  Manage users, subscriptions, and plans from a single interface. This panel is completely separate from the regular user dashboard.
                </p>
              </div>
            </div>
            <div className="flex flex-wrap gap-3">
              <Button
                variant="outline"
                className="border-slate-700 bg-slate-900 text-slate-100 hover:bg-slate-800"
                onClick={() => navigate("/projects")}
              >
                <ArrowLeft className="mr-2 h-4 w-4" />
                Back to projects
              </Button>
              <Button className="bg-blue-600 hover:bg-blue-500" onClick={handleRefresh} disabled={refreshing || loading}>
                {refreshing ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <RefreshCw className="mr-2 h-4 w-4" />}
                Refresh
              </Button>
            </div>
          </div>
        </div>

        {loading ? (
          <div className="flex items-center justify-center py-20">
            <Loader2 className="h-8 w-8 animate-spin text-blue-400" />
          </div>
        ) : (
          <>
            <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
              <Card className="border-slate-800 bg-slate-900/80 text-slate-50">
                <CardHeader className="pb-2">
                  <CardTitle className="flex items-center gap-2 text-sm text-slate-300">
                    <Users className="h-4 w-4 text-blue-400" />
                    Total Users
                  </CardTitle>
                </CardHeader>
                <CardContent className="text-3xl font-bold">{dashboard?.totalUsers ?? 0}</CardContent>
              </Card>

              <Card className="border-slate-800 bg-slate-900/80 text-slate-50">
                <CardHeader className="pb-2">
                  <CardTitle className="flex items-center gap-2 text-sm text-slate-300">
                    <ShieldCheck className="h-4 w-4 text-amber-400" />
                    Blocked Users
                  </CardTitle>
                </CardHeader>
                <CardContent className="text-3xl font-bold">{dashboard?.blockedUsers ?? 0}</CardContent>
              </Card>

              <Card className="border-slate-800 bg-slate-900/80 text-slate-50">
                <CardHeader className="pb-2">
                  <CardTitle className="flex items-center gap-2 text-sm text-slate-300">
                    <Wallet className="h-4 w-4 text-emerald-400" />
                    Active Subscriptions
                  </CardTitle>
                </CardHeader>
                <CardContent className="space-y-1">
                  <div className="text-3xl font-bold">{dashboard?.activeSubscriptions ?? 0}</div>
                  <p className="text-xs text-slate-400">{paidPlanCount} paid plans configured</p>
                </CardContent>
              </Card>
            </div>

            <div className="grid grid-cols-1 gap-6 xl:grid-cols-[1.15fr_0.85fr]">
              <Card className="border-slate-800 bg-slate-900/80 text-slate-50">
                <CardHeader className="space-y-4">
                  <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                    <div>
                      <CardTitle>User Management</CardTitle>
                      <p className="text-sm text-slate-400">Search, block, unblock, or delete users.</p>
                    </div>
                    <div className="flex w-full gap-2 sm:w-auto">
                      <Input
                        value={query}
                        onChange={(e) => setQuery(e.target.value)}
                        onKeyDown={(e) => {
                          if (e.key === "Enter") {
                            handleSearch();
                          }
                        }}
                        placeholder="Search by name or email"
                        className="border-slate-700 bg-slate-950 text-slate-50 placeholder:text-slate-500 sm:w-72"
                      />
                      <Button
                        onClick={handleSearch}
                        className="bg-slate-100 text-slate-900 hover:bg-slate-200"
                      >
                        Search
                      </Button>
                    </div>
                  </div>
                </CardHeader>
                <CardContent className="space-y-3">
                  {users.map((user) => (
                    <div
                      key={user.id}
                      className="flex flex-col gap-4 rounded-2xl border border-slate-800 bg-slate-950/70 p-4 md:flex-row md:items-center md:justify-between"
                    >
                      <div className="space-y-1">
                        <p className="font-medium">
                          {user.name} <span className="text-slate-400">({user.username})</span>
                        </p>
                        <div className="flex flex-wrap items-center gap-2 text-xs">
                          <Badge variant="secondary" className="bg-slate-800 text-slate-200">
                            {user.role}
                          </Badge>
                          {user.blocked && <Badge variant="destructive">Blocked</Badge>}
                        </div>
                      </div>

                      {user.role !== "ADMIN" && (
                        <div className="flex flex-wrap gap-2">
                          <Button
                            variant={user.blocked ? "outline" : "destructive"}
                            className={cn(
                              user.blocked && "border-emerald-700 text-emerald-300 hover:bg-emerald-950 hover:text-emerald-200"
                            )}
                            onClick={async () => {
                              try {
                                await api.setAdminUserBlocked(user.id, !user.blocked);
                                toast({
                                  title: user.blocked ? "User unblocked" : "User blocked",
                                  description: `${user.name} updated successfully.`,
                                });
                                await load(query);
                              } catch (error) {
                                toast({
                                  title: "User update failed",
                                  description: error instanceof Error ? error.message : "Please try again.",
                                  variant: "destructive",
                                });
                              }
                            }}
                          >
                            {user.blocked ? "Unblock" : "Block"}
                          </Button>

                          <Button
                            variant="outline"
                            className="border-red-900 text-red-300 hover:bg-red-950 hover:text-red-200"
                            onClick={async () => {
                              try {
                                await api.deleteAdminUser(user.id);
                                toast({ title: "User removed", description: `${user.name} deleted successfully.` });
                                await load(query);
                              } catch (error) {
                                toast({
                                  title: "Delete failed",
                                  description: error instanceof Error ? error.message : "Please try again.",
                                  variant: "destructive",
                                });
                              }
                            }}
                          >
                            <Trash2 className="mr-2 h-4 w-4" />
                            Delete
                          </Button>
                        </div>
                      )}
                    </div>
                  ))}
                </CardContent>
              </Card>

              <Card className="border-slate-800 bg-slate-900/80 text-slate-50">
                <CardHeader className="space-y-4">
                  <div className="flex items-center justify-between gap-3">
                    <div>
                      <CardTitle>Plan Management</CardTitle>
                      <p className="text-sm text-slate-400">Create, edit, activate, and remove subscription plans.</p>
                    </div>
                    <Button onClick={openCreatePlanDialog} className="bg-blue-600 hover:bg-blue-500">
                      <Plus className="mr-2 h-4 w-4" />
                      Add Plan
                    </Button>
                  </div>
                </CardHeader>
                <CardContent className="space-y-3">
                  {plans.map((plan) => (
                    <div key={plan.id} className="space-y-3 rounded-2xl border border-slate-800 bg-slate-950/70 p-4">
                      <div className="flex items-start justify-between gap-3">
                        <div>
                          <p className="font-medium">{plan.displayName ?? plan.name}</p>
                          <p className="text-sm text-slate-400">
                            {formatPrice(plan.priceInPaise)} | {plan.validityDays ?? 30} days
                          </p>
                        </div>
                        <Badge
                          className={cn(
                            "border",
                            (plan.priceInPaise ?? 0) > 0
                              ? "border-emerald-800 bg-emerald-950 text-emerald-300"
                              : "border-slate-700 bg-slate-800 text-slate-200"
                          )}
                        >
                          {(plan.name ?? "PLAN").toUpperCase()}
                        </Badge>
                      </div>

                      <div className="grid grid-cols-2 gap-2 text-xs text-slate-300">
                        <div>Projects: {plan.maxProjects ?? "N/A"}</div>
                        <div>Tokens/day: {plan.unlimitedAi ? "Unlimited" : (plan.maxTokensPerDay ?? "N/A")}</div>
                      </div>

                      <div className="flex items-center justify-between gap-3 rounded-xl border border-slate-800 px-3 py-2">
                        <div>
                          <p className="text-sm font-medium">Plan visibility</p>
                          <p className="text-xs text-slate-400">Inactive plans will not be visible on the public billing page.</p>
                        </div>
                        <Switch
                          checked={Boolean(plan.active)}
                          onCheckedChange={async (checked) => {
                            try {
                              await api.setAdminPlanActive(plan.id, checked);
                              toast({
                                title: checked ? "Plan activated" : "Plan deactivated",
                                description: `${plan.name} updated successfully.`,
                              });
                              await load(query);
                            } catch (error) {
                              toast({
                                title: "Plan status update failed",
                                description: error instanceof Error ? error.message : "Please try again.",
                                variant: "destructive",
                              });
                            }
                          }}
                        />
                      </div>

                      <div className="flex flex-wrap gap-2">
                        <Button
                          variant="outline"
                          className="border-slate-700 bg-slate-900 hover:bg-slate-800"
                          onClick={() => openEditPlanDialog(plan)}
                        >
                          Edit
                        </Button>
                        <Button
                          variant="outline"
                          className="border-red-900 text-red-300 hover:bg-red-950 hover:text-red-200"
                          onClick={() => setPlanToDelete(plan)}
                        >
                          Remove
                        </Button>
                      </div>
                    </div>
                  ))}
                </CardContent>
              </Card>
            </div>
          </>
        )}
      </div>

      <Dialog
        open={planDialogOpen}
        onOpenChange={(open) => {
          setPlanDialogOpen(open);
          if (!open) resetPlanEditor();
        }}
      >
        <DialogContent className="border-slate-800 bg-slate-950 text-slate-50 sm:max-w-2xl">
          <DialogHeader>
            <DialogTitle>{editingPlan ? "Edit plan" : "Create new plan"}</DialogTitle>
            <DialogDescription className="text-slate-400">
              Subscription cards on the billing page will be rendered using this configuration.
            </DialogDescription>
          </DialogHeader>

          <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
            <div className="space-y-2">
              <Label htmlFor="plan-name">Plan name</Label>
              <Input
                id="plan-name"
                value={planForm.name}
                onChange={(e) => setPlanForm((prev) => ({ ...prev, name: e.target.value }))}
                className="border-slate-700 bg-slate-900"
              />
            </div>

            <div className="space-y-2">
              <Label htmlFor="plan-price">Price in Rupees (INR)</Label>
              <Input
                id="plan-price"
                type="number"
                min="0"
                value={(planForm.priceInPaise ?? 0) / 100}
                onChange={(e) => setPlanForm((prev) => ({ ...prev, priceInPaise: Number(e.target.value || 0) * 100 }))}
                className="border-slate-700 bg-slate-900"
              />
            </div>

            <div className="space-y-2">
              <Label htmlFor="plan-projects">Max projects</Label>
              <Input
                id="plan-projects"
                type="number"
                min="0"
                value={planForm.maxProjects ?? 0}
                onChange={(e) => setPlanForm((prev) => ({ ...prev, maxProjects: Number(e.target.value || 0) }))}
                className="border-slate-700 bg-slate-900"
              />
            </div>

            <div className="space-y-2">
              <Label htmlFor="plan-validity">Validity days</Label>
              <Input
                id="plan-validity"
                type="number"
                min="1"
                value={planForm.validityDays}
                onChange={(e) => setPlanForm((prev) => ({ ...prev, validityDays: Number(e.target.value || 1) }))}
                className="border-slate-700 bg-slate-900"
              />
            </div>

            <div className="space-y-2">
              <Label htmlFor="plan-tokens">Max AI tokens per day</Label>
              <Input
                id="plan-tokens"
                type="number"
                min="0"
                disabled={planForm.unlimitedAi}
                value={planForm.maxTokensPerDay ?? 0}
                onChange={(e) => setPlanForm((prev) => ({ ...prev, maxTokensPerDay: Number(e.target.value || 0) }))}
                className="border-slate-700 bg-slate-900 disabled:opacity-50"
              />
            </div>

            <div className="flex flex-col justify-end gap-4 rounded-2xl border border-slate-800 bg-slate-900/70 p-4">
              <div className="flex items-center justify-between">
                <div>
                  <Label>Unlimited AI</Label>
                  <p className="text-xs text-slate-400">Enabling this will bypass daily token limits.</p>
                </div>
                <Switch
                  checked={planForm.unlimitedAi}
                  onCheckedChange={(checked) => setPlanForm((prev) => ({ ...prev, unlimitedAi: checked }))}
                />
              </div>

              <div className="flex items-center justify-between">
                <div>
                  <Label>Active plan</Label>
                  <p className="text-xs text-slate-400">Disabling this will hide the plan from new subscribers.</p>
                </div>
                <Switch
                  checked={planForm.active}
                  onCheckedChange={(checked) => setPlanForm((prev) => ({ ...prev, active: checked }))}
                />
              </div>
            </div>
          </div>

          <DialogFooter>
            <Button
              variant="outline"
              className="border-slate-700 bg-slate-900 hover:bg-slate-800"
              onClick={() => setPlanDialogOpen(false)}
            >
              Cancel
            </Button>
            <Button onClick={handlePlanSubmit} disabled={submitting} className="bg-blue-600 hover:bg-blue-500">
              {submitting && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
              {editingPlan ? "Save changes" : "Create plan"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <AlertDialog open={Boolean(planToDelete)} onOpenChange={(open) => !open && setPlanToDelete(null)}>
        <AlertDialogContent className="border-slate-800 bg-slate-950 text-slate-50">
          <AlertDialogHeader>
            <AlertDialogTitle>Remove plan?</AlertDialogTitle>
            <AlertDialogDescription className="text-slate-400">
              Removing {planToDelete?.name} will hide it from the billing page. If active subscriptions exist under this plan, the request will be blocked by the server.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel className="border-slate-700 bg-slate-900 text-slate-100 hover:bg-slate-800">
              Cancel
            </AlertDialogCancel>
            <AlertDialogAction
              className="bg-red-600 text-white hover:bg-red-500"
              onClick={async () => {
                if (!planToDelete) return;

                try {
                  await api.deleteAdminPlan(planToDelete.id);
                  toast({ title: "Plan removed", description: `${planToDelete.name} deleted successfully.` });
                  setPlanToDelete(null);
                  await load(query);
                } catch (error) {
                  toast({
                    title: "Plan delete failed",
                    description: error instanceof Error ? error.message : "Please try again.",
                    variant: "destructive",
                  });
                }
              }}
            >
              Remove plan
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}
