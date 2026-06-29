import { useState, useEffect } from "react";
import { useNavigate, Link } from "react-router-dom";
import { Loader2, Mail, Sparkles, User, Lock, AlertCircle, ArrowLeft } from "lucide-react";
import { api, setAuthToken, setUserInfo } from "@/lib/api";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { useToast } from "@/hooks/use-toast";

type SignupStep = 1 | 2 | 3;

export default function Signup() {
  const [step, setStep] = useState<SignupStep>(1);
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [verificationCode, setVerificationCode] = useState("");
  const [password, setPassword] = useState("");

  const [isLoading, setIsLoading] = useState(false);
  const [isResending, setIsResending] = useState(false);
  const [serverError, setServerError] = useState<string | null>(null);

  // Resend OTP Countdown timer
  const [resendCountdown, setResendCountdown] = useState(30);

  const navigate = useNavigate();
  const { toast } = useToast();

  // Reset timer on moving to Step 2
  useEffect(() => {
    if (step === 2) {
      setResendCountdown(30);
    }
  }, [step]);

  // Countdown timer logic
  useEffect(() => {
    if (resendCountdown <= 0) return;
    const timer = setInterval(() => {
      setResendCountdown((prev) => prev - 1);
    }, 1000);
    return () => clearInterval(timer);
  }, [resendCountdown]);

  // RFC 5322 simplified — same regex as backend EmailServiceImpl
  const isEmailValid = (val: string) =>
    /^[a-zA-Z0-9_+&*\-]+(?:\.[a-zA-Z0-9_+&*\-]+)*@(?:[a-zA-Z0-9\-]+\.)+[a-zA-Z]{2,}$/.test(
      val.trim().toLowerCase()
    );

  const handleSendOtp = async (e: React.FormEvent) => {
    e.preventDefault();
    setServerError(null);

    if (!name.trim()) {
      setServerError("Please enter your full name.");
      return;
    }

    if (!email.trim()) {
      setServerError("Please enter your email address.");
      return;
    }

    if (!isEmailValid(email)) {
      setServerError("Email is incorrect. Please enter a correct email address.");
      return;
    }

    setIsLoading(true);
    try {
      await api.sendSignupOtp({
        email: email.trim().toLowerCase(),
      });
      toast({ title: "Sent ✓", description: `Verification code sent to ${email.trim().toLowerCase()}.` });
      setStep(2);
    } catch (error) {
      const msg = error instanceof Error ? error.message : "Failed to send verification code";
      setServerError(msg);
    } finally {
      setIsLoading(false);
    }
  };

  const handleVerifyOtp = async (e: React.FormEvent) => {
    e.preventDefault();
    setServerError(null);

    if (verificationCode.trim().length !== 6) {
      setServerError("Please enter the complete 6-digit verification code.");
      return;
    }

    setIsLoading(true);
    try {
      await api.verifySignupOtp({
        email: email.trim().toLowerCase(),
        code: verificationCode.trim(),
      });
      toast({ title: "Email Verified!", description: "Please set your secure password." });
      setStep(3);
    } catch (error) {
      const msg = error instanceof Error ? error.message : "Invalid verification code";
      setServerError(msg);
    } finally {
      setIsLoading(false);
    }
  };

  const handleResendOtp = async () => {
    setServerError(null);
    setIsResending(true);
    try {
      await api.sendSignupOtp({
        email: email.trim().toLowerCase(),
      });
      toast({ title: "Sent ✓", description: `New verification code sent to ${email.trim().toLowerCase()}.` });
      setResendCountdown(30);
    } catch (error) {
      const msg = error instanceof Error ? error.message : "Failed to resend code";
      setServerError(msg);
    } finally {
      setIsResending(false);
    }
  };

  const handleCompleteSignup = async (e: React.FormEvent) => {
    e.preventDefault();
    setServerError(null);

    if (!password) {
      setServerError("Please enter a password.");
      return;
    }

    if (password.length < 4) {
      setServerError("Password must be at least 4 characters.");
      return;
    }

    setIsLoading(true);
    try {
      const response = await api.completeSignup({
        name: name.trim(),
        email: email.trim().toLowerCase(),
        password,
      });

      if (response.token && response.user) {
        setAuthToken(response.token);
        setUserInfo({
          id: response.user.id,
          username: response.user.username,
          name: response.user.name,
          role: response.user.role,
        });
        toast({ title: "Welcome!", description: "Account created successfully" });
        navigate("/projects");
      } else {
        setServerError("Signup failed. Please try again.");
      }
    } catch (error) {
      const msg = error instanceof Error ? error.message : "Failed to create account";
      setServerError(msg);
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center p-4 bg-background">
      <div className="absolute inset-0 overflow-hidden pointer-events-none">
        <div className="absolute top-1/3 left-1/3 w-[600px] h-[600px] bg-primary/5 rounded-full blur-3xl" />
      </div>

      <div className="relative w-full max-w-md">
        <div className="bg-card border border-border/50 rounded-2xl p-8 shadow-2xl">

          {/* Back button */}
          {step > 1 && (
            <button
              onClick={() => {
                setStep((prev) => (prev - 1) as SignupStep);
                setServerError(null);
              }}
              className="inline-flex items-center text-sm text-muted-foreground hover:text-foreground mb-4 transition-colors"
            >
              <ArrowLeft className="w-4 h-4 mr-1.5" />
              Back
            </button>
          )}

          <div className="text-center mb-8">
            <div className="inline-flex items-center justify-center w-14 h-14 rounded-xl bg-primary/20 mb-5">
              <Sparkles className="w-7 h-7 text-primary" />
            </div>
            <h1 className="text-2xl font-semibold text-foreground mb-2">
              Create an account
            </h1>
            <p className="text-muted-foreground text-sm">
              {step === 1 && "Verify your email to get started"}
              {step === 2 && "Enter the 6-digit verification code sent to your email"}
              {step === 3 && "Set up your account password"}
            </p>
          </div>

          {/* Server Error Banner */}
          {serverError && (
            <div className="flex items-start gap-3 mb-5 p-3 rounded-xl bg-destructive/10 border border-destructive/20 text-destructive text-sm">
              <AlertCircle className="w-4 h-4 mt-0.5 shrink-0" />
              <span>{serverError}</span>
            </div>
          )}

          {/* STEP 1: ENTER NAME & EMAIL */}
          {step === 1 && (
            <form onSubmit={handleSendOtp} className="space-y-5">
              <div className="space-y-2">
                <Label htmlFor="name" className="text-sm font-medium">
                  Full Name
                </Label>
                <div className="relative">
                  <User className="absolute left-3.5 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
                  <Input
                    id="name"
                    type="text"
                    placeholder="John Doe"
                    value={name}
                    onChange={(e) => setName(e.target.value)}
                    className="pl-10 h-12 bg-muted/50 border-border/50 focus:border-primary rounded-xl text-sm"
                    disabled={isLoading}
                    autoComplete="name"
                  />
                </div>
              </div>

              <div className="space-y-2">
                <Label htmlFor="email" className="text-sm font-medium">
                  Email Address
                </Label>
                <div className="relative">
                  <Mail className="absolute left-3.5 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
                  <Input
                    id="email"
                    type="email"
                    placeholder="you@example.com"
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                    className="pl-10 h-12 bg-muted/50 border-border/50 focus:border-primary rounded-xl text-sm"
                    disabled={isLoading}
                    autoComplete="email"
                  />
                </div>
              </div>

              <Button
                type="submit"
                disabled={isLoading}
                className="w-full h-12 bg-primary hover:bg-primary/90 font-medium rounded-xl text-sm"
              >
                {isLoading ? (
                  <>
                    <Loader2 className="w-4 h-4 mr-2 animate-spin" />
                    Sending OTP...
                  </>
                ) : (
                  "Send Verification Code"
                )}
              </Button>
            </form>
          )}

          {/* STEP 2: VERIFY OTP CODE */}
          {step === 2 && (
            <form onSubmit={handleVerifyOtp} className="space-y-5">
              <div className="space-y-2">
                <Label htmlFor="verify-otp" className="text-sm font-medium">
                  Verification Code
                </Label>
                <Input
                  id="verify-otp"
                  type="text"
                  placeholder="Enter 6-digit OTP"
                  maxLength={6}
                  value={verificationCode}
                  onChange={(e) =>
                    setVerificationCode(e.target.value.replace(/\D/g, "").slice(0, 6))
                  }
                  className="h-12 bg-muted/50 border-border/50 focus:border-primary rounded-xl text-center text-xl font-bold tracking-widest"
                  disabled={isLoading}
                  autoFocus
                />
              </div>

              <Button
                type="submit"
                disabled={isLoading || verificationCode.length !== 6}
                className="w-full h-12 bg-primary hover:bg-primary/90 font-medium rounded-xl text-sm"
              >
                {isLoading ? (
                  <>
                    <Loader2 className="w-4 h-4 mr-2 animate-spin" />
                    Verifying...
                  </>
                ) : (
                  "Verify Email Code"
                )}
              </Button>

              <div className="mt-6 text-center text-sm">
                <button
                  type="button"
                  onClick={handleResendOtp}
                  disabled={isResending || isLoading || resendCountdown > 0}
                  className="text-primary hover:underline font-medium disabled:opacity-50 disabled:no-underline"
                >
                  {isResending
                    ? "Sending..."
                    : resendCountdown > 0
                    ? `Resend OTP in ${resendCountdown}s`
                    : "Resend OTP Code"}
                </button>
              </div>
            </form>
          )}

          {/* STEP 3: SET PASSWORD */}
          {step === 3 && (
            <form onSubmit={handleCompleteSignup} className="space-y-5">
              <div className="space-y-2">
                <Label className="text-sm font-medium">Verified Email</Label>
                <div className="relative">
                  <Mail className="absolute left-3.5 top-1/2 -translate-y-1/2 w-4 h-4 text-emerald-500" />
                  <Input
                    type="text"
                    value={email}
                    className="pl-10 h-12 bg-muted/30 border-emerald-500/35 text-emerald-500 rounded-xl text-sm cursor-not-allowed"
                    disabled
                  />
                  <div className="absolute right-4 top-1/2 -translate-y-1/2 text-emerald-500 font-medium text-xs flex items-center gap-1 bg-emerald-500/10 px-2.5 py-1 rounded-full border border-emerald-500/20">
                    ✓ Verified
                  </div>
                </div>
              </div>

              <div className="space-y-2">
                <Label htmlFor="password" className="text-sm font-medium">
                  Create Password
                </Label>
                <div className="relative">
                  <Lock className="absolute left-3.5 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
                  <Input
                    id="password"
                    type="password"
                    placeholder="Min. 4 characters"
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    className="pl-10 h-12 bg-muted/50 border-border/50 focus:border-primary rounded-xl text-sm"
                    disabled={isLoading}
                    autoFocus
                  />
                </div>
              </div>

              <Button
                type="submit"
                disabled={isLoading}
                className="w-full h-12 bg-primary hover:bg-primary/90 font-medium rounded-xl text-sm"
              >
                {isLoading ? (
                  <>
                    <Loader2 className="w-4 h-4 mr-2 animate-spin" />
                    Creating account...
                  </>
                ) : (
                  "Create Account & Sign in"
                )}
              </Button>
            </form>
          )}

          {step === 1 && (
            <p className="text-center text-sm text-muted-foreground mt-6">
              Already have an account?{" "}
              <Link to="/login" className="text-primary hover:underline font-medium">
                Sign in
              </Link>
            </p>
          )}
        </div>
      </div>
    </div>
  );
}
