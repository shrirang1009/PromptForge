import { useState, useEffect } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import {
  ArrowLeft,
  Zap,
  BarChart2,
  CreditCard,
  CheckCircle,
  Loader2,
  ExternalLink,
  RefreshCw,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Progress } from "@/components/ui/progress";
import { api, getAuthToken } from "@/lib/api";
import { getUserInfo } from "@/lib/api";
import { SubscriptionResponse, UsageTodayResponse, PlanLimitsResponse, PublicPlanResponse } from "@/lib/types";
import { useToast } from "@/hooks/use-toast";
import { cn } from "@/lib/utils";

declare global {
  interface Window {
    RzpCreditCardOptions: any;
    Razorpay: any;
  }
}

export default function BillingPage() {
  const navigate = useNavigate();
  const { toast } = useToast();
  const [searchParams] = useSearchParams();

  const [subscriptions, setSubscriptions] = useState<SubscriptionResponse[]>([]);
  const [usage, setUsage] = useState<UsageTodayResponse | null>(null);
  const [limits, setLimits] = useState<PlanLimitsResponse | null>(null);
  const [plans, setPlans] = useState<PublicPlanResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [checkingOut, setCheckingOut] = useState<number | null>(null);
  const [openingPortal, setOpeningPortal] = useState(false);
  const [razorpayLoaded, setRazorpayLoaded] = useState(false);
  const [cancelingId, setCancelingId] = useState<number | null>(null);

  // Handle success/cancel from Razorpay redirect
  useEffect(() => {
    const success = searchParams.get("success");
    const canceled = searchParams.get("canceled");
    const manage = searchParams.get("manage");

    if (success === "1") {
      toast({
        title: "Payment Successful",
        description: "Your subscription has been activated!",
        variant: "default",
      });
      load();
    } else if (canceled === "1") {
      toast({
        title: "Payment Canceled",
        description: "Your payment was not completed.",
        variant: "destructive",
      });
    } else if (manage === "1") {
      toast({
        title: "Billing Management",
        description: "Contact support to manage your subscription.",
      });
    }
  }, [searchParams]);

  // Load Razorpay script
  useEffect(() => {
    const script = document.createElement("script");
    script.src = "https://checkout.razorpay.com/v1/checkout.js";
    script.async = true;
    script.onload = () => {
      setRazorpayLoaded(true);
    };
    script.onerror = () => {
      console.error("Failed to load Razorpay SDK");
    };
    document.body.appendChild(script);

    return () => {
      document.body.removeChild(script);
    };
  }, []);

  const load = async () => {
    setLoading(true);
    try {
      const [sub, use, lim] = await Promise.allSettled([
        api.getMySubscription(),
        api.getUsageToday(),
        api.getPlanLimits(),
      ]);
      const plansResp = await api.getPlans().catch(() => []);
      if (sub.status === "fulfilled") setSubscriptions(sub.value);
      if (use.status === "fulfilled") setUsage(use.value);
      if (lim.status === "fulfilled") setLimits(lim.value);
      setPlans(plansResp);
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
  }, []);

  const handleCheckout = async (planId: number) => {
    if (!razorpayLoaded || !window.Razorpay) {
      toast({
        title: "Razorpay not loaded",
        description: "Please refresh the page and try again.",
        variant: "destructive",
      });
      return;
    }

    setCheckingOut(planId);
    try {
      // Create checkout session on backend
      const checkout = await api.createCheckout({
        planId
      });
      if (!checkout.orderId || !checkout.keyId || !checkout.amount || !checkout.currency) {
        throw new Error("Checkout session is incomplete. Please try again.");
      }

      const userInfo = getUserInfo();

      // Open Razorpay checkout
      const options = {
        key: checkout.keyId,
        amount: checkout.amount,
        currency: checkout.currency,
        order_id: checkout.orderId,
        name: "PromptForge",
        description: "Subscription Plan",
        handler: async function (response: any) {
          try {
            await api.confirmPayment({
              orderId: checkout.orderId!,
              paymentId: response.razorpay_payment_id,
            });
            toast({
              title: "Payment Successful",
              description: "Your plan is now active.",
            });
            await load();
          } catch (e) {
            toast({
              title: "Payment received, activation pending",
              description: e instanceof Error ? e.message : "Please refresh after a few seconds.",
              variant: "destructive",
            });
          } finally {
            setCheckingOut(null);
          }
        },
        prefill: {
          name: checkout.customerName || userInfo?.name || "User",
          email: checkout.customerEmail || userInfo?.username || "",
        },
        theme: {
          color: "#2563eb",
        },
        modal: {
          ondismiss: function () {
            setCheckingOut(null);
          },
        },
      };

      const rzp = new window.Razorpay(options);
      rzp.on("payment.failed", function (response: any) {
        toast({
          title: "Payment Failed",
          description: response.error.description || "Payment failed. Please try again.",
          variant: "destructive",
        });
        setCheckingOut(null);
      });
      rzp.open();
    } catch (err) {
      toast({
        title: "Checkout failed",
        description: err instanceof Error ? err.message : "Something went wrong",
        variant: "destructive",
      });
      setCheckingOut(null);
    }
  };

  const handlePortal = async () => {
    setOpeningPortal(true);
    try {
      const { portalUrl } = await api.openPortal();
      window.location.href = portalUrl;
    } catch (err) {
      toast({
        title: "Portal unavailable",
        description: err instanceof Error ? err.message : "Could not open billing portal",
        variant: "destructive",
      });
    } finally {
      setOpeningPortal(false);
    }
  };

  const handleCancelSubscription = async (id: number) => {
    if (!window.confirm("Are you sure you want to cancel this plan?")) {
      return;
    }
    setCancelingId(id);
    try {
      await api.cancelSubscription(id);
      toast({
        title: "Plan Canceled",
        description: "Your subscription has been canceled successfully.",
      });
      await load();
    } catch (err) {
      toast({
        title: "Cancellation failed",
        description: err instanceof Error ? err.message : "Something went wrong",
        variant: "destructive",
      });
    } finally {
      setCancelingId(null);
    }
  };

  const normalizedSubs = Array.isArray(subscriptions) ? subscriptions : (subscriptions ? [subscriptions] : []);
  const currentPlanName = normalizedSubs.length > 0 ? (normalizedSubs[0]?.plan?.name?.toUpperCase() ?? "FREE") : "FREE";
  const activePlanNames = normalizedSubs.map((sub) => sub.plan?.name?.toUpperCase() || "FREE");
  const uiPlans = plans.map((plan) => {
    const priceInPaise = plan.priceInPaise ?? 0;
    const validityDays = plan.validityDays ?? 30;
    const monthly = validityDays >= 28 && validityDays <= 31;
    const priceDisplay = priceInPaise === 0 ? "INR 0" : `INR ${Math.round(priceInPaise / 100)}`;
    const features: string[] = [];
    if (plan.maxProjects != null) {
      features.push(plan.maxProjects >= 999999 ? "Unlimited projects" : `${plan.maxProjects} projects`);
    }
    if (plan.unlimitedAi) {
      features.push("Unlimited AI messages");
    } else if (plan.maxTokensPerDay != null) {
      features.push(`${plan.maxTokensPerDay.toLocaleString()} AI messages/day`);
    }
    if (plan.name?.toUpperCase() === "FREE") {
      features.push("Community support");
    } else if (plan.name?.toUpperCase() === "PRO") {
      features.push("Priority support");
      features.push("Download ZIP");
    } else {
      features.push("Team collaboration");
    }
    return {
      id: plan.id,
      name: (plan.name ?? "").toUpperCase(),
      displayName: plan.displayName ?? plan.name,
      price: priceInPaise,
      priceDisplay,
      period: priceInPaise === 0 ? "free" : (monthly ? "per month" : `for ${validityDays} days`),
      features,
      highlight: plan.name?.toUpperCase() === "PRO",
    };
  });

  const tokensUsed = usage?.tokensUsed ?? (normalizedSubs[0]?.tokensUsedThisCycle ?? 0);
  const tokensLimit = usage?.tokensLimit ?? limits?.maxTokensPerDay ?? 100;
  const tokensRemaining = usage?.tokensRemaining ?? Math.max(tokensLimit - tokensUsed, 0);
  const tokensPercent = Math.min(100, Math.round((tokensUsed / tokensLimit) * 100));

  return (
    <div className="min-h-screen bg-background">
      {/* Header */}
      <header className="border-b border-border/40 bg-background/95 backdrop-blur">
        <div className="container flex h-14 max-w-screen-lg items-center gap-4 px-4 sm:px-8">
          <Button variant="ghost" size="icon" onClick={() => navigate("/projects")}>
            <ArrowLeft className="w-4 h-4" />
          </Button>
          <h1 className="font-semibold text-lg">Billing &amp; Subscription</h1>
          <Button
            variant="ghost"
            size="icon"
            className="ml-auto"
            onClick={load}
            disabled={loading}
          >
            <RefreshCw className={cn("w-4 h-4", loading && "animate-spin")} />
          </Button>
        </div>
      </header>

      <main className="container max-w-screen-lg py-8 px-4 sm:px-8 space-y-10">
        {/* Current Plan + Usage */}
        {loading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-8 h-8 animate-spin text-primary" />
          </div>
        ) : (
          <>
            {/* Status Cards */}
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
              {/* Current Plan */}
              <Card>
                <CardHeader className="pb-2">
                  <CardTitle className="text-sm text-muted-foreground flex items-center gap-1.5">
                    <Zap className="w-4 h-4" /> Current Plan
                  </CardTitle>
                </CardHeader>
                <CardContent className="space-y-4">
                  {normalizedSubs.length === 0 ? (
                    <div>
                      <p className="text-2xl font-bold">FREE</p>
                      <p className="text-xs text-green-500 mt-1 font-medium uppercase tracking-wide">
                        ACTIVE
                      </p>
                    </div>
                  ) : (
                    normalizedSubs.map((sub) => (
                      <div key={sub.id} className="pb-3 border-b border-border/40 last:border-0 last:pb-0 flex items-center justify-between gap-4">
                        <div>
                          <p className="text-lg font-bold">{sub.plan?.displayName ?? sub.plan?.name}</p>
                          <p
                            className={cn(
                              "text-xs mt-0.5 font-medium uppercase tracking-wide",
                              sub.status === "ACTIVE" ? "text-green-500" : "text-amber-500"
                            )}
                          >
                            {sub.status ?? "—"}
                          </p>
                          {sub.currentPeriodEnd && (
                            <p className="text-[11px] text-muted-foreground mt-0.5">
                              Renews{" "}
                              {new Date(sub.currentPeriodEnd).toLocaleDateString()}
                            </p>
                          )}
                        </div>
                        {sub.id && (
                          <Button
                            variant="destructive"
                            size="sm"
                            className="h-8"
                            onClick={() => handleCancelSubscription(sub.id!)}
                            disabled={cancelingId === sub.id}
                          >
                            {cancelingId === sub.id ? (
                              <Loader2 className="w-3.5 h-3.5 animate-spin" />
                            ) : (
                              "Cancel"
                            )}
                          </Button>
                        )}
                      </div>
                    ))
                  )}
                </CardContent>
              </Card>

              {/* AI Usage Today */}
              <Card>
                <CardHeader className="pb-2">
                  <CardTitle className="text-sm text-muted-foreground flex items-center gap-1.5">
                    <BarChart2 className="w-4 h-4" /> AI Usage Today
                  </CardTitle>
                </CardHeader>
                <CardContent className="space-y-2">
                  <div className="flex justify-between text-sm">
                    <span className="font-medium">{tokensUsed.toLocaleString()} tokens</span>
                    <span className="text-muted-foreground">/ {tokensLimit.toLocaleString()}</span>
                  </div>
                  <Progress value={tokensPercent} className="h-2" />
                  <p className="text-xs text-muted-foreground">
                    {tokensPercent}% used, {tokensRemaining.toLocaleString()} remaining
                  </p>
                </CardContent>
              </Card>

              {/* Manage Billing */}
              <Card>
                <CardHeader className="pb-2">
                  <CardTitle className="text-sm text-muted-foreground flex items-center gap-1.5">
                    <CreditCard className="w-4 h-4" /> Manage Billing
                  </CardTitle>
                </CardHeader>
                <CardContent>
                  <p className="text-sm text-muted-foreground mb-3">
                    Update payment method, download invoices, cancel subscription.
                  </p>
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={handlePortal}
                    disabled={openingPortal}
                    className="w-full"
                  >
                    {openingPortal ? (
                      <Loader2 className="w-3.5 h-3.5 mr-2 animate-spin" />
                    ) : (
                      <ExternalLink className="w-3.5 h-3.5 mr-2" />
                    )}
                    Open Portal
                  </Button>
                </CardContent>
              </Card>
            </div>

            {/* Plan Cards */}
            <div>
              <h2 className="text-xl font-semibold mb-4">Choose a Plan</h2>
              <div className="grid grid-cols-1 sm:grid-cols-3 gap-6">
                {uiPlans.map((plan) => {
                  const isCurrent = plan.price === 0 ? normalizedSubs.length === 0 : activePlanNames.includes(plan.name);
                  const isFree = plan.price === 0;
                  return (
                    <Card
                      key={plan.id}
                      className={cn(
                        "relative flex flex-col",
                        plan.highlight && "border-primary shadow-lg"
                      )}
                    >
                      {plan.highlight && (
                        <div className="absolute -top-3 left-1/2 -translate-x-1/2 bg-primary text-primary-foreground text-[10px] font-bold uppercase tracking-wider px-3 py-0.5 rounded-full">
                          Most Popular
                        </div>
                      )}
                      <CardHeader>
                        <CardTitle className="flex items-center justify-between">
                          <span>{plan.displayName}</span>
                          {isCurrent && (
                            <span className="text-[10px] font-bold uppercase tracking-wider bg-primary/10 text-primary px-2 py-0.5 rounded-full">
                              Current
                            </span>
                          )}
                        </CardTitle>
                        <div className="mt-1">
                          <span className="text-3xl font-extrabold">{plan.priceDisplay}</span>
                          <span className="text-sm text-muted-foreground ml-1">/{plan.period}</span>
                        </div>
                      </CardHeader>
                      <CardContent className="flex flex-col flex-1 gap-4">
                        <ul className="space-y-2 flex-1">
                          {plan.features.map((f) => (
                            <li key={f} className="flex items-start gap-2 text-sm">
                              <CheckCircle className="w-4 h-4 text-green-500 shrink-0 mt-0.5" />
                              {f}
                            </li>
                          ))}
                        </ul>
                        <Button
                          className="w-full"
                          variant={plan.highlight ? "default" : "outline"}
                          disabled={isCurrent || checkingOut === plan.id}
                          onClick={() => {
                            if (isFree) {
                              // Handle free plan selection
                              toast({
                                title: "Free Plan",
                                description: "You're already on the free plan!",
                              });
                            } else if (plan.price > 0) {
                              handleCheckout(plan.id);
                            }
                          }}
                        >
                          {checkingOut === plan.id ? (
                            <Loader2 className="w-4 h-4 mr-2 animate-spin" />
                          ) : null}
                          {isCurrent ? "Current Plan" : `Upgrade to ${plan.displayName}`}
                        </Button>
                      </CardContent>
                    </Card>
                  );
                })}
              </div>
            </div>
          </>
        )}
      </main>
    </div>
  );
}
