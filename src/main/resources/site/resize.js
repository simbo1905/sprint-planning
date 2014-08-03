//  Cameron Adams resize script from http://www.themaninblue.com/experiment/ResolutionLayout/

function getBrowserWidth()
{
	if (window.innerWidth)
	{
		return window.innerWidth;
	}
	else if (document.documentElement && document.documentElement.clientWidth != 0)
	{
		return document.documentElement.clientWidth;
	}
	else if (document.body)
	{
		return document.body.clientWidth;
	}
	
	return 0;
};

function attachEventListener(target, eventType, functionRef, capture)
{
    if (typeof target.addEventListener != "undefined")
    {
        target.addEventListener(eventType, functionRef, capture);
    }
    else if (typeof target.attachEvent != "undefined")
    {
        target.attachEvent("on" + eventType, functionRef);
    }
    else
    {
        return false;
    }

    return true;
};

function windowSizeEffects()
{
	var theWidth = getBrowserWidth();

	if( theWidth <= 600  ) 
	{
		document.getElementById("sprint-planning-large.png").src = "site/sprint-planning-web.png";
	}
	else 
	{
		document.getElementById("sprint-planning-large.png").src = "site/sprint-planning-large.png";
	}

	return true;
}

attachEventListener(window, "resize", windowSizeEffects, false);

windowSizeEffects();
